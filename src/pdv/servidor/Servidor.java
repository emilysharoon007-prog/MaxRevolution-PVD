package pdv.servidor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;
import pdv.Empresa;
import pdv.dados.Banco;
import pdv.dados.Relatorios;
import pdv.dados.Repositorio;
import pdv.modelo.Cliente;
import pdv.modelo.Entidade;
import pdv.modelo.Fornecedor;
import pdv.modelo.ItemVenda;
import pdv.modelo.OrdemServico;
import pdv.modelo.Produto;
import pdv.modelo.Usuario;
import pdv.modelo.Venda;
import pdv.util.ApiException;
import pdv.util.Json;
import pdv.util.Seguranca;

/**
 * BACK END do sistema: um servidor HTTP (biblioteca nativa do Java, sem frameworks)
 * que faz duas coisas ao mesmo tempo:
 *
 *  1) Entrega os arquivos do FRONT END (HTML, CSS e JavaScript da pasta "web").
 *  2) Responde a API REST em JSON, sob o caminho "/api/...", que o front end usa
 *     para ler e gravar os dados (produtos, clientes, vendas, etc.).
 *
 * Rotas principais da API:
 *   POST /api/login                -> entra no sistema e recebe um token
 *   GET  /api/config               -> dados da empresa e constantes (público)
 *   CRUD /api/produtos | clientes | fornecedores | ordens | usuarios
 *   GET  /api/vendas, POST /api/vendas, POST /api/vendas/simular, POST /api/vendas/{id}/cancelar
 *   GET  /api/dashboard, GET /api/relatorios
 */
public class Servidor {

    /** true = mostra no login os botões de acesso de demonstração (definido pelo Main). */
    public static volatile boolean modoDemo = true;

    private final Banco banco;
    private final Path raizWeb;

    /** Sessões ativas: token de login -> id do usuário. */
    private final Map<String, Integer> sessoes = new ConcurrentHashMap<>();

    /** Trava usada para que duas vendas simultâneas não "gastem" o mesmo estoque. */
    private final Object travaVendas = new Object();

    /** Tipos de arquivo (MIME) que o servidor sabe entregar. */
    private static final Map<String, String> TIPOS_MIME = Map.of(
        ".html", "text/html; charset=utf-8",
        ".css",  "text/css; charset=utf-8",
        ".js",   "application/javascript; charset=utf-8",
        ".json", "application/json; charset=utf-8",
        ".svg",  "image/svg+xml",
        ".png",  "image/png",
        ".jpg",  "image/jpeg",
        ".ico",  "image/x-icon"
    );

    /**
     * @param banco   base de dados (repositórios)
     * @param raizWeb pasta com os arquivos do front end
     */
    public Servidor(Banco banco, Path raizWeb) {
        this.banco = banco;
        this.raizWeb = raizWeb.toAbsolutePath().normalize();
    }

    /**
     * Liga o servidor.
     *
     * @param porta porta TCP desejada
     * @param rede  true = aceita acessos de outros computadores da rede; false = só este computador
     */
    public void iniciar(int porta, boolean rede) throws IOException {
        InetAddress endereco = rede ? InetAddress.getByName("0.0.0.0") : InetAddress.getLoopbackAddress();
        HttpServer http = HttpServer.create(new InetSocketAddress(endereco, porta), 0);
        http.createContext("/", this::tratar);                       // todas as requisições passam por "tratar"
        http.setExecutor(Executors.newFixedThreadPool(8));           // 8 threads atendendo em paralelo
        http.start();
    }

    // =====================================================================
    //  ENTRADA DAS REQUISIÇÕES
    // =====================================================================

    /** Recebe toda requisição, decide se é API ou arquivo estático e trata os erros. */
    private void tratar(HttpExchange ex) throws IOException {
        try {
            String caminho = ex.getRequestURI().getPath();
            if (caminho.startsWith("/api/")) {
                api(ex, caminho);
            } else {
                arquivoEstatico(ex, caminho);
            }
        } catch (ApiException e) {                      // erro "esperado": regra de negócio, login, permissão...
            enviarJson(ex, e.getStatus(), erro(e.getMessage()));
        } catch (IllegalArgumentException e) {          // dado inválido enviado pelo usuário
            enviarJson(ex, 400, erro(e.getMessage()));
        } catch (Exception e) {                         // qualquer outra falha é um bug nosso
            e.printStackTrace();
            enviarJson(ex, 500, erro("Erro interno do servidor."));
        } finally {
            ex.close();
        }
    }

    /** Monta o JSON padrão de erro: {"erro": "mensagem"}. */
    private static Map<String, Object> erro(String mensagem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("erro", mensagem);
        return m;
    }

    // =====================================================================
    //  FRONT END: arquivos estáticos
    // =====================================================================

    /** Entrega um arquivo da pasta "web" (index.html, css, js...). */
    private void arquivoEstatico(HttpExchange ex, String caminho) throws IOException {
        String relativo = caminho.equals("/") ? "index.html" : caminho.substring(1);
        Path alvo = raizWeb.resolve(relativo).normalize();

        // Segurança: impede acessar arquivos fora da pasta web (ex.: "/../../senha.txt")
        if (!alvo.startsWith(raizWeb) || !Files.isRegularFile(alvo)) {
            byte[] msg = "404 - Arquivo não encontrado".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
            return;
        }

        String nome = alvo.getFileName().toString();
        String extensao = nome.contains(".") ? nome.substring(nome.lastIndexOf('.')) : "";
        byte[] conteudo = Files.readAllBytes(alvo);
        ex.getResponseHeaders().set("Content-Type", TIPOS_MIME.getOrDefault(extensao, "application/octet-stream"));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, conteudo.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(conteudo);
        }
    }

    // =====================================================================
    //  API: roteamento
    // =====================================================================

    /** Interpreta a URL da API (/api/recurso/id/acao), lê o corpo e chama a rota certa. */
    private void api(HttpExchange ex, String caminho) throws IOException {
        String metodo = ex.getRequestMethod();
        String[] partes = caminho.substring("/api/".length()).split("/");
        String recurso = partes[0];

        // Exemplos: /api/produtos/3 -> id=3 | /api/vendas/simular -> acao="simular" | /api/vendas/5/cancelar -> id=5, acao="cancelar"
        Integer id = null;
        String acao = null;
        if (partes.length > 1) {
            if (partes[1].matches("\\d+")) {
                id = Integer.valueOf(partes[1]);
                if (partes.length > 2) {
                    acao = partes[2];
                }
            } else {
                acao = partes[1];
            }
        }

        Object corpo = null;
        if (metodo.equals("POST") || metodo.equals("PUT")) {
            String texto = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!texto.isBlank()) {
                corpo = Json.parse(texto);
            }
        }

        Object resposta = rotear(ex, metodo, recurso, id, acao, corpo);
        enviarJson(ex, 200, resposta);
    }

    /** Escolhe o que fazer de acordo com o recurso pedido. */
    private Object rotear(HttpExchange ex, String metodo, String recurso, Integer id, String acao, Object corpo) {

        // ---- Rotas públicas (não exigem login) ----
        if (recurso.equals("config") && metodo.equals("GET")) {
            return config();
        }
        if (recurso.equals("login") && metodo.equals("POST")) {
            return login(Json.mapa(corpo));
        }

        // ---- A partir daqui o usuário precisa estar logado ----
        Usuario usuario = autenticar(ex);

        switch (recurso) {
            case "eu":
                return dadosDeLogin(usuario);

            case "logout":
                sessoes.remove(tokenDe(ex));
                return erroSimples("ok", "Sessão encerrada.");

            case "dashboard":
                exigirModulo(usuario, "dashboard");
                return Relatorios.dashboard(banco.vendas.listar(), banco.produtos.listar(),
                                            banco.clientes.listar(), banco.ordens.listar());

            case "relatorios":
                exigirModulo(usuario, "relatorios");
                return Relatorios.completo(banco.vendas.listar());

            // Produtos, clientes e fornecedores podem ser LIDOS por qualquer usuário logado
            // (o PDV e as ordens de serviço precisam dessas listas), mas só quem tem o módulo pode alterar.
            case "produtos":
                return crud(metodo, id, corpo, usuario, "produtos", true,
                            banco.produtos, Produto::fromMap, regrasProduto());
            case "clientes":
                return crud(metodo, id, corpo, usuario, "clientes", true,
                            banco.clientes, Cliente::fromMap, regrasNenhuma());
            case "fornecedores":
                return crud(metodo, id, corpo, usuario, "fornecedores", true,
                            banco.fornecedores, Fornecedor::fromMap, regrasNenhuma());

            case "ordens":
                return crud(metodo, id, corpo, usuario, "ordens", false,
                            banco.ordens, OrdemServico::fromMap, regrasOrdem());
            case "usuarios":
                return crud(metodo, id, corpo, usuario, "usuarios", false,
                            banco.usuarios, Usuario::fromMap, regrasUsuario());

            case "vendas":
                return vendas(metodo, id, acao, corpo, usuario);

            default:
                throw new ApiException(404, "Rota não encontrada: /api/" + recurso);
        }
    }

    private static Map<String, Object> erroSimples(String chave, String valor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(chave, valor);
        return m;
    }

    // =====================================================================
    //  LOGIN, SESSÃO E PERMISSÕES
    // =====================================================================

    /** Valida login/senha e devolve um token de sessão. */
    private Object login(Map<String, Object> corpo) {
        String login = Json.texto(corpo, "login").toLowerCase();
        Object senhaDigitada = corpo.get("senha");
        String senha = senhaDigitada == null ? "" : String.valueOf(senhaDigitada);

        Usuario encontrado = null;
        for (Usuario u : banco.usuarios.listar()) {
            if (u.getLogin().equals(login)) {
                encontrado = u;
                break;
            }
        }
        if (encontrado == null || !encontrado.isAtivo() || !Seguranca.conferir(senha, encontrado.getSenhaHash())) {
            try {
                Thread.sleep(400);        // pequena demora dificulta tentativas automáticas de adivinhar senha
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(401, "Usuário ou senha inválidos.");
        }

        String token = UUID.randomUUID().toString();    // identificador aleatório da sessão
        sessoes.put(token, encontrado.getId());
        Map<String, Object> resposta = dadosDeLogin(encontrado);
        resposta.put("token", token);
        return resposta;
    }

    /** Dados do usuário logado + módulos que ele pode acessar (o menu é montado a partir daqui). */
    private Map<String, Object> dadosDeLogin(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usuario", u.paraApi());
        m.put("modulos", Seguranca.modulosDoPerfil(u.getPerfil()));
        return m;
    }

    /** Lê o token enviado no cabeçalho "Authorization: Bearer xxxx". */
    private String tokenDe(HttpExchange ex) {
        String cabecalho = ex.getRequestHeaders().getFirst("Authorization");
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            return cabecalho.substring(7).trim();
        }
        return "";
    }

    /** Descobre quem é o usuário pelo token; gera erro 401 se não estiver logado. */
    private Usuario autenticar(HttpExchange ex) {
        Integer idUsuario = sessoes.get(tokenDe(ex));
        Usuario u = idUsuario == null ? null : banco.usuarios.buscar(idUsuario);
        if (u == null || !u.isAtivo()) {
            throw new ApiException(401, "Sessão expirada. Faça login novamente.");
        }
        return u;
    }

    /** Consulta a matriz de permissões; gera erro 403 se o perfil não pode acessar o módulo. */
    private void exigirModulo(Usuario u, String modulo) {
        if (!Seguranca.permitido(u.getPerfil(), modulo)) {
            throw new ApiException(403, "Seu perfil (" + u.getPerfil() + ") não tem permissão para esta operação.");
        }
    }

    // =====================================================================
    //  CONFIGURAÇÃO PÚBLICA
    // =====================================================================

    /** Dados da empresa e constantes (vetores/matrizes) que o front end também precisa conhecer. */
    private Object config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("empresa", Empresa.toMap());
        m.put("demo", modoDemo);
        m.put("categorias", Produto.CATEGORIAS);
        m.put("formasPagamento", Venda.FORMAS_PAGAMENTO);
        m.put("tiposCliente", Cliente.TIPOS);
        m.put("statusOS", OrdemServico.STATUS);
        m.put("faixasAtacado", Venda.FAIXAS_ATACADO);            // matriz de descontos
        m.put("perfis", Seguranca.PERFIS);
        m.put("modulos", Seguranca.MODULOS);
        m.put("permissoes", Seguranca.matriz());                 // matriz perfil x módulo
        return m;
    }

    // =====================================================================
    //  CRUD GENÉRICO (criar, listar, alterar, excluir)
    // =====================================================================

    /**
     * Regras extras de cada cadastro (validações que dependem de outros registros).
     * Cada cadastro pode sobrescrever apenas o que precisar.
     */
    private abstract static class Regras<T> {
        /** Executada antes de gravar (criação ou alteração). "antigo" é null na criação. */
        void antesSalvar(T novo, T antigo, Usuario quemFaz) { }
        /** Executada antes de excluir. */
        void antesRemover(T existente, Usuario quemFaz) { }
    }

    private <T> Regras<T> regrasNenhuma() {
        return new Regras<T>() { };
    }

    /**
     * Implementação única do CRUD, reaproveitada por todos os cadastros.
     *
     * @param leituraLivre true = qualquer usuário logado pode listar/consultar
     */
    private <T extends Entidade> Object crud(String metodo, Integer id, Object corpo, Usuario usuario,
                                              String modulo, boolean leituraLivre,
                                              Repositorio<T> repo, Function<Map<String, Object>, T> fabrica,
                                              Regras<T> regras) {
        // ---------- LISTAR / CONSULTAR ----------
        if (metodo.equals("GET")) {
            if (!leituraLivre) {
                exigirModulo(usuario, modulo);
            }
            if (id == null) {
                List<Object> saida = new ArrayList<>();
                for (T item : repo.listar()) {
                    saida.add(item.paraApi());
                }
                return saida;
            }
            return repo.buscarOuErro(id).paraApi();
        }

        // As demais operações alteram dados: exigem permissão no módulo
        exigirModulo(usuario, modulo);

        // ---------- CRIAR ----------
        if (metodo.equals("POST") && id == null) {
            T novo = fabrica.apply(Json.mapa(corpo));
            novo.setId(0);
            novo.validar();
            regras.antesSalvar(novo, null, usuario);
            repo.salvar(novo);
            return novo.paraApi();
        }

        // ---------- ALTERAR ----------
        if (metodo.equals("PUT") && id != null) {
            T antigo = repo.buscarOuErro(id);
            T novo = fabrica.apply(Json.mapa(corpo));
            novo.setId(id);
            novo.validar();
            regras.antesSalvar(novo, antigo, usuario);
            repo.salvar(novo);
            return novo.paraApi();
        }

        // ---------- EXCLUIR ----------
        if (metodo.equals("DELETE") && id != null) {
            T existente = repo.buscarOuErro(id);
            regras.antesRemover(existente, usuario);
            repo.remover(id);
            return erroSimples("ok", "Registro excluído.");
        }

        throw new ApiException(405, "Operação não permitida.");
    }

    // =====================================================================
    //  REGRAS ESPECÍFICAS DE CADA CADASTRO
    // =====================================================================

    /** Produto: código único e fornecedor existente. */
    private Regras<Produto> regrasProduto() {
        return new Regras<Produto>() {
            @Override
            void antesSalvar(Produto novo, Produto antigo, Usuario quemFaz) {
                for (Produto outro : banco.produtos.listar()) {
                    if (outro.getId() != novo.getId() && outro.getCodigo().equalsIgnoreCase(novo.getCodigo())) {
                        throw new ApiException(400, "Já existe um produto com o código '" + novo.getCodigo() + "'.");
                    }
                }
                if (novo.getFornecedorId() > 0 && banco.fornecedores.buscar(novo.getFornecedorId()) == null) {
                    throw new ApiException(400, "Fornecedor informado não existe.");
                }
            }
        };
    }

    /** Ordem de serviço: preenche nome do cliente, datas e técnico automaticamente. */
    private Regras<OrdemServico> regrasOrdem() {
        return new Regras<OrdemServico>() {
            @Override
            void antesSalvar(OrdemServico novo, OrdemServico antigo, Usuario quemFaz) {
                // Cliente cadastrado: usa o nome do cadastro. Avulso: usa o nome digitado.
                if (novo.getClienteId() > 0) {
                    Cliente c = banco.clientes.buscar(novo.getClienteId());
                    if (c == null) {
                        throw new ApiException(400, "Cliente informado não existe.");
                    }
                    novo.setClienteNome(c.getNome());
                } else if (novo.getClienteNome().isBlank()) {
                    novo.setClienteNome("Cliente avulso");
                }
                if (novo.getTecnico().isBlank()) {
                    novo.setTecnico(quemFaz.getNome());
                }
                // Datas: a entrada nunca muda; a saída é marcada quando a OS é entregue
                novo.setDataEntrada(antigo != null ? antigo.getDataEntrada() : agora());
                if ("Entregue".equals(novo.getStatus())) {
                    boolean jaTinhaSaida = antigo != null && !antigo.getDataSaida().isBlank();
                    novo.setDataSaida(jaTinhaSaida ? antigo.getDataSaida() : agora());
                } else {
                    novo.setDataSaida("");
                }
            }
        };
    }

    /** Usuário: login único, senha obrigatória na criação e sempre ao menos um ADMIN ativo. */
    private Regras<Usuario> regrasUsuario() {
        return new Regras<Usuario>() {
            @Override
            void antesSalvar(Usuario novo, Usuario antigo, Usuario quemFaz) {
                for (Usuario outro : banco.usuarios.listar()) {
                    if (outro.getId() != novo.getId() && outro.getLogin().equals(novo.getLogin())) {
                        throw new ApiException(400, "Já existe um usuário com o login '" + novo.getLogin() + "'.");
                    }
                }
                if (novo.getSenhaHash().isBlank()) {
                    if (antigo == null) {
                        throw new ApiException(400, "Informe uma senha para o novo usuário (mínimo 4 caracteres).");
                    }
                    novo.setSenhaHash(antigo.getSenhaHash());     // senha em branco na edição = manter a atual
                }
                // Não deixa o sistema ficar sem nenhum administrador ativo
                boolean continuaAdmin = novo.isAtivo() && novo.getPerfil().equals("ADMIN");
                if (!continuaAdmin && !existeOutroAdminAtivo(novo.getId())) {
                    throw new ApiException(400, "O sistema precisa de pelo menos um administrador ativo.");
                }
            }

            @Override
            void antesRemover(Usuario existente, Usuario quemFaz) {
                if (existente.getId() == quemFaz.getId()) {
                    throw new ApiException(400, "Você não pode excluir o próprio usuário.");
                }
                if (!existeOutroAdminAtivo(existente.getId())) {
                    throw new ApiException(400, "O sistema precisa de pelo menos um administrador ativo.");
                }
            }
        };
    }

    /** Verifica se existe algum administrador ativo diferente do usuário informado. */
    private boolean existeOutroAdminAtivo(int idIgnorado) {
        for (Usuario u : banco.usuarios.listar()) {
            if (u.getId() != idIgnorado && u.isAtivo() && u.getPerfil().equals("ADMIN")) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  VENDAS (PDV)
    // =====================================================================

    /** Rotas de /api/vendas: listar, simular, registrar e cancelar. */
    private Object vendas(String metodo, Integer id, String acao, Object corpo, Usuario usuario) {

        // GET /api/vendas  ou  GET /api/vendas/{id}
        if (metodo.equals("GET")) {
            exigirModulo(usuario, "vendas");
            if (id != null) {
                return banco.vendas.buscarOuErro(id).toMap();
            }
            List<Venda> todas = banco.vendas.listar();
            todas.sort(Comparator.comparingInt(Venda::getId).reversed());     // mais recentes primeiro
            List<Object> saida = new ArrayList<>();
            for (Venda v : todas) {
                saida.add(v.toMap());
            }
            return saida;
        }

        // POST /api/vendas/simular -> só calcula os totais (usado pelo carrinho), não grava nada
        if (metodo.equals("POST") && id == null && "simular".equals(acao)) {
            exigirModulo(usuario, "pdv");
            return montarVenda(Json.mapa(corpo), usuario, false).toMap();
        }

        // POST /api/vendas -> finaliza a venda de verdade
        if (metodo.equals("POST") && id == null && acao == null) {
            exigirModulo(usuario, "pdv");
            return montarVenda(Json.mapa(corpo), usuario, true).toMap();
        }

        // POST /api/vendas/{id}/cancelar -> apenas administrador
        if (metodo.equals("POST") && id != null && "cancelar".equals(acao)) {
            if (!usuario.getPerfil().equals("ADMIN")) {
                throw new ApiException(403, "Somente o administrador pode cancelar vendas.");
            }
            return cancelarVenda(id).toMap();
        }

        throw new ApiException(405, "Operação não permitida em /api/vendas.");
    }

    /**
     * Monta a venda a partir do carrinho enviado pelo navegador.
     * Valida estoque, escolhe o preço (varejo/atacado), aplica descontos e,
     * se {@code gravar} for true, baixa o estoque e salva a venda.
     */
    private Venda montarVenda(Map<String, Object> corpo, Usuario usuario, boolean gravar) {
        synchronized (travaVendas) {      // uma venda por vez: evita vender o mesmo estoque duas vezes

            // ---- Tipo e forma de pagamento ----
            String tipo = Json.texto(corpo, "tipo").toUpperCase();
            if (tipo.isEmpty()) {
                tipo = Venda.TIPOS[0];
            }
            if (Seguranca.indice(Venda.TIPOS, tipo) < 0) {
                throw new ApiException(400, "Tipo de venda inválido.");
            }
            String forma = Json.texto(corpo, "formaPagamento");
            if (forma.isEmpty()) {
                forma = Venda.FORMAS_PAGAMENTO[0];
            }
            if (Seguranca.indice(Venda.FORMAS_PAGAMENTO, forma) < 0) {
                throw new ApiException(400, "Forma de pagamento inválida.");
            }

            // ---- Cliente (0 = consumidor final) ----
            int clienteId = Json.inteiro(corpo, "clienteId");
            String clienteNome = "Consumidor final";
            if (clienteId > 0) {
                Cliente c = banco.clientes.buscar(clienteId);
                if (c == null) {
                    throw new ApiException(400, "Cliente não encontrado.");
                }
                clienteNome = c.getNome();
            }

            // ---- Itens: junta linhas repetidas do mesmo produto somando as quantidades ----
            List<Object> linhas = Json.lista(corpo, "itens");
            if (linhas.isEmpty()) {
                throw new ApiException(400, "Adicione ao menos um item à venda.");
            }
            Map<Integer, Integer> quantidadePorProduto = new LinkedHashMap<>();
            for (Object linha : linhas) {
                Map<String, Object> l = Json.mapa(linha);
                int produtoId = Json.inteiro(l, "produtoId");
                int quantidade = Json.inteiro(l, "quantidade");
                if (quantidade <= 0) {
                    throw new ApiException(400, "A quantidade de cada item deve ser maior que zero.");
                }
                quantidadePorProduto.merge(produtoId, quantidade, Integer::sum);
            }

            // ---- Converte para o VETOR de ItemVenda, conferindo o estoque ----
            ItemVenda[] itens = new ItemVenda[quantidadePorProduto.size()];
            Produto[] produtosDosItens = new Produto[itens.length];
            int posicao = 0;
            for (Map.Entry<Integer, Integer> e : quantidadePorProduto.entrySet()) {
                Produto p = banco.produtos.buscar(e.getKey());
                if (p == null) {
                    throw new ApiException(400, "Produto não encontrado (id " + e.getKey() + ").");
                }
                if (e.getValue() > p.getEstoque()) {
                    throw new ApiException(400, "Estoque insuficiente para \"" + p.getNome()
                        + "\" (disponível: " + p.getEstoque() + ").");
                }
                // Atacado usa o preço de atacado (se não houver, cai para o de varejo)
                double preco = (tipo.equals("ATACADO") && p.getPrecoAtacado() > 0)
                    ? p.getPrecoAtacado() : p.getPrecoVarejo();
                itens[posicao] = new ItemVenda(p, e.getValue(), preco);
                produtosDosItens[posicao] = p;
                posicao++;
            }

            // ---- Monta a venda e calcula totais ----
            Venda venda = new Venda();
            venda.setTipo(tipo);
            venda.setFormaPagamento(forma);
            venda.setClienteId(clienteId);
            venda.setClienteNome(clienteNome);
            venda.setVendedor(usuario.getNome());
            venda.setItens(itens);
            venda.setData(agora());
            venda.recalcular(Json.numero(corpo, "desconto"));

            // ---- Grava: baixa o estoque e salva a venda ----
            if (gravar) {
                for (int i = 0; i < itens.length; i++) {
                    Produto p = produtosDosItens[i];
                    p.setEstoque(p.getEstoque() - itens[i].getQuantidade());   // baixa automática
                    banco.produtos.salvar(p);
                }
                banco.vendas.salvar(venda);
            }
            return venda;
        }
    }

    /** Cancela uma venda e devolve os itens ao estoque. */
    private Venda cancelarVenda(int id) {
        synchronized (travaVendas) {
            Venda venda = banco.vendas.buscarOuErro(id);
            if (!venda.concluida()) {
                throw new ApiException(400, "Esta venda já está cancelada.");
            }
            for (ItemVenda item : venda.getItens()) {
                Produto p = banco.produtos.buscar(item.getProdutoId());
                if (p != null) {                                   // o produto pode ter sido excluído
                    p.setEstoque(p.getEstoque() + item.getQuantidade());   // devolve ao estoque
                    banco.produtos.salvar(p);
                }
            }
            venda.setStatus("CANCELADA");
            banco.vendas.salvar(venda);
            return venda;
        }
    }

    // =====================================================================
    //  UTILITÁRIOS
    // =====================================================================

    /** Data e hora atuais no formato ISO (sem milissegundos). */
    private static String agora() {
        return LocalDateTime.now().withNano(0).toString();
    }

    /** Envia um objeto como resposta JSON com o status HTTP informado. */
    private void enviarJson(HttpExchange ex, int status, Object objeto) throws IOException {
        byte[] bytes = Json.stringify(objeto).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}

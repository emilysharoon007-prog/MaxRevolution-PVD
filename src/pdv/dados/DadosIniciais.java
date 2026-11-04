package pdv.dados;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import pdv.modelo.Cliente;
import pdv.modelo.Fornecedor;
import pdv.modelo.ItemVenda;
import pdv.modelo.OrdemServico;
import pdv.modelo.Produto;
import pdv.modelo.Usuario;
import pdv.modelo.Venda;
import pdv.util.Seguranca;

/**
 * Cadastra dados de exemplo na PRIMEIRA execução, para o sistema já abrir
 * "vivo" (com estoque, clientes, vendas e gráficos) na hora da apresentação.
 *
 * Para voltar ao estado inicial, basta apagar a pasta "dados" e abrir o sistema de novo.
 */
public final class DadosIniciais {

    private DadosIniciais() { }

    /**
     * Só popula se o banco estiver vazio (não sobrescreve dados reais).
     *
     * @param demo true = cadastra produtos, clientes, vendas e ordens de exemplo;
     *             false = cadastra apenas os administradores (uso real, sem dados de teste)
     */
    public static void popularSeVazio(Banco banco, boolean demo) {
        if (!demo) {
            garantirAdministradores(banco);
            return;
        }
        if (banco.usuarios.vazio()) {
            criarUsuarios(banco);
        }
        if (banco.produtos.vazio()) {
            criarFornecedores(banco);
            criarProdutos(banco);
        }
        if (banco.clientes.vazio()) {
            criarClientes(banco);
        }
        if (banco.vendas.vazio()) {
            criarVendas(banco);
        }
        if (banco.ordens.vazio()) {
            criarOrdens(banco);
        }
        garantirAdministradores(banco);
    }

    /**
     * Garante que Davi e Emilly existam como administradores, mesmo em uma pasta
     * "dados" antiga (criada antes de eles serem cadastrados). Senha inicial: 1234.
     */
    private static void garantirAdministradores(Banco banco) {
        String[][] admins = { { "Davi", "davi" }, { "Emilly", "emilly" } };   // { nome, login }
        // Na internet, defina a variável PDV_SENHA_ADMIN para não usar a senha 1234
        String definida = System.getenv("PDV_SENHA_ADMIN");
        String senhaInicial = (definida == null || definida.length() < 4) ? "1234" : definida;
        for (String[] a : admins) {
            boolean existe = false;
            for (Usuario u : banco.usuarios.listar()) {
                if (u.getLogin().equals(a[1])) {
                    existe = true;
                }
            }
            if (!existe) {
                Usuario novo = new Usuario();
                novo.setNome(a[0]);
                novo.setLogin(a[1]);
                novo.setSenhaHash(Seguranca.gerarHash(senhaInicial));
                novo.setPerfil("ADMIN");
                banco.usuarios.salvar(novo);
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Usuários de demonstração (um de cada perfil)
    // ---------------------------------------------------------------------
    private static void criarUsuarios(Banco banco) {
        // VETOR de matrizes de texto: cada linha = { nome, login, senha, perfil }
        String[][] usuarios = {
            { "Davi",          "davi",   "1234",     "ADMIN"    },
            { "Emilly",        "emilly", "1234",     "ADMIN"    },
            { "Carla Souza",   "carla",  "venda123", "VENDEDOR" },
            { "Rafael Lima",   "rafael", "tec123",   "TECNICO"  },
        };
        for (String[] dados : usuarios) {
            Usuario u = new Usuario();
            u.setNome(dados[0]);
            u.setLogin(dados[1]);
            u.setSenhaHash(Seguranca.gerarHash(dados[2]));
            u.setPerfil(dados[3]);
            banco.usuarios.salvar(u);
        }
    }

    // ---------------------------------------------------------------------
    //  Fornecedores
    // ---------------------------------------------------------------------
    private static void criarFornecedores(Banco banco) {
        // MATRIZ de texto: cada linha = { razão social, CNPJ, contato, telefone, e-mail, o que fornece }
        String[][] linhas = {
            { "TechParts Distribuidora Ltda", "11.111.111/0001-11", "Marcos Dias",  "(00) 4000-1111", "vendas@techparts.example",  "Telas, baterias e peças de reposição" },
            { "Celular Center Importadora",   "22.222.222/0001-22", "Patrícia Reis", "(00) 4000-2222", "pedidos@celularcenter.example", "Aparelhos e acessórios de celular" },
            { "InfoHard Distribuição",        "33.333.333/0001-33", "Eduardo Faria", "(00) 4000-3333", "comercial@infohard.example",   "Hardware e periféricos" },
            { "SoftLicenças Brasil",          "44.444.444/0001-44", "Luciana Prado", "(00) 4000-4444", "contato@softlicencas.example", "Licenças de software e antivírus" },
        };
        for (String[] l : linhas) {
            Fornecedor f = new Fornecedor();
            f.setRazaoSocial(l[0]);
            f.setDocumento(l[1]);
            f.setContato(l[2]);
            f.setTelefone(l[3]);
            f.setEmail(l[4]);
            f.setProdutosFornecidos(l[5]);
            banco.fornecedores.salvar(f);
        }
    }

    // ---------------------------------------------------------------------
    //  Produtos
    // ---------------------------------------------------------------------
    private static void criarProdutos(Banco banco) {
        // Os ids dos fornecedores acima são 1 (peças), 2 (celulares), 3 (hardware) e 4 (software).
        // Cada linha: código, nome, categoria, fornecedor, custo, varejo, atacado, estoque, mínimo
        Object[][] linhas = {
            { "APR001", "Smartphone 128GB Preto",          "Aparelhos",       2,  780.00, 1099.90, 960.00,  12,  3 },
            { "APR002", "Smartphone 64GB Azul",            "Aparelhos",       2,  520.00,  799.90, 690.00,   8,  3 },
            { "APR003", "Celular Básico Dual Chip",        "Aparelhos",       2,   90.00,  159.90, 125.00,  20,  5 },
            { "ACE001", "Capinha de Silicone Universal",   "Acessórios",      2,    6.00,   24.90,  14.00, 120, 30 },
            { "ACE002", "Película de Vidro 3D",            "Acessórios",      2,    4.00,   19.90,   9.00, 150, 40 },
            { "ACE003", "Carregador Turbo 20W USB-C",      "Acessórios",      2,   28.00,   69.90,  45.00,  40, 10 },
            { "ACE004", "Cabo USB-C 1m Reforçado",         "Acessórios",      2,    8.00,   29.90,  17.00,  90, 20 },
            { "ACE005", "Fone de Ouvido Bluetooth",        "Acessórios",      2,   35.00,   89.90,  60.00,   4,  8 },
            { "ACE006", "Power Bank 10.000mAh",            "Acessórios",      2,   55.00,  119.90,  85.00,  18,  5 },
            { "PEC001", "Tela LCD de Reposição (Kit)",     "Peças (Atacado)", 1,   85.00,  189.90, 130.00,  35, 10 },
            { "PEC002", "Bateria de Reposição Universal",  "Peças (Atacado)", 1,   22.00,   69.90,  42.00,  60, 15 },
            { "PEC003", "Conector de Carga Tipo-C",        "Peças (Atacado)", 1,    5.00,   19.90,   9.00, 200, 50 },
            { "PEC004", "Alto-falante Auricular",          "Peças (Atacado)", 1,    7.00,   24.90,  13.00,  80, 20 },
            { "PEC005", "Câmera Traseira de Reposição",    "Peças (Atacado)", 1,   38.00,   99.90,  68.00,   6, 10 },
            { "HRD001", "SSD 480GB SATA",                  "Hardware",        3,  140.00,  229.90, 185.00,  25,  6 },
            { "HRD002", "Memória RAM 8GB DDR4",            "Hardware",        3,  120.00,  199.90, 160.00,  22,  6 },
            { "HRD003", "Fonte ATX 500W",                  "Hardware",        3,  130.00,  219.90, 175.00,   3,  5 },
            { "HRD004", "Cooler para Processador",         "Hardware",        3,   25.00,   59.90,  42.00,  30,  8 },
            { "HRD005", "HD Externo 1TB",                  "Hardware",        3,  230.00,  349.90, 290.00,  10,  3 },
            { "SFT001", "Antivírus 1 Ano (licença)",       "Software",        4,   30.00,   89.90,  55.00,  50, 10 },
            { "SFT002", "Pacote Escritório (licença)",     "Software",        4,  120.00,  249.90, 190.00,  20,  5 },
            { "SFT003", "Sistema Operacional (licença)",   "Software",        4,  180.00,  349.90, 270.00,  15,  5 },
            { "PER001", "Mouse sem Fio",                   "Periféricos",     3,   22.00,   49.90,  35.00,  45, 10 },
            { "PER002", "Teclado ABNT2 USB",               "Periféricos",     3,   30.00,   69.90,  48.00,  38, 10 },
            { "PER003", "Webcam Full HD",                  "Periféricos",     3,   75.00,  149.90, 110.00,  14,  4 },
            { "PER004", "Headset Gamer",                   "Periféricos",     3,   90.00,  189.90, 140.00,  12,  4 },
        };
        for (Object[] l : linhas) {
            Produto p = new Produto();
            p.setCodigo((String) l[0]);
            p.setNome((String) l[1]);
            p.setCategoria((String) l[2]);
            p.setFornecedorId((Integer) l[3]);
            p.setPrecoCusto((Double) l[4]);
            p.setPrecoVarejo((Double) l[5]);
            p.setPrecoAtacado((Double) l[6]);
            p.setEstoque((Integer) l[7]);
            p.setEstoqueMinimo((Integer) l[8]);
            banco.produtos.salvar(p);
        }
    }

    // ---------------------------------------------------------------------
    //  Clientes
    // ---------------------------------------------------------------------
    private static void criarClientes(Banco banco) {
        // MATRIZ de texto: nome, documento, telefone, e-mail, endereço, tipo
        String[][] linhas = {
            { "Maria Silva",           "111.111.111-11",     "(00) 90000-1111", "maria@example.com",      "Rua A, 10",           "VAREJO"  },
            { "João Pereira",          "222.222.222-22",     "(00) 90000-2222", "joao@example.com",       "Rua B, 20",           "VAREJO"  },
            { "Ana Costa",             "333.333.333-33",     "(00) 90000-3333", "ana@example.com",        "Av. C, 300",          "VAREJO"  },
            { "Carlos Mendes",         "444.444.444-44",     "(00) 90000-4444", "carlos@example.com",     "Rua D, 40",           "VAREJO"  },
            { "Assistec Norte Ltda",   "55.555.555/0001-55", "(00) 3000-5555",  "compras@assistec.example", "Av. Industrial, 500",  "ATACADO" },
            { "Celulares do Zé ME",    "66.666.666/0001-66", "(00) 3000-6666",  "ze@celulares.example",   "Rua do Comércio, 60", "ATACADO" },
        };
        for (String[] l : linhas) {
            Cliente c = new Cliente();
            c.setNome(l[0]);
            c.setDocumento(l[1]);
            c.setTelefone(l[2]);
            c.setEmail(l[3]);
            c.setEndereco(l[4]);
            c.setTipo(l[5]);
            banco.clientes.salvar(c);
        }
    }

    // ---------------------------------------------------------------------
    //  Vendas dos últimos ~5 meses (para o dashboard e os relatórios terem dados)
    // ---------------------------------------------------------------------
    private static void criarVendas(Banco banco) {
        Random sorteio = new Random(7);          // semente fixa: sempre gera as mesmas vendas
        List<Produto> produtos = banco.produtos.listar();
        List<Cliente> clientes = banco.clientes.listar();
        List<Venda> geradas = new ArrayList<>();
        int[] quantidadesAtacado = {10, 12, 20, 30, 50};          // VETOR de quantidades típicas do atacado

        // No atacado os lojistas compram itens baratos em quantidade (peças e acessórios)
        List<Produto> produtosAtacado = new ArrayList<>();
        for (Produto p : produtos) {
            if (p.getCategoria().equals("Peças (Atacado)") || p.getCategoria().equals("Acessórios")) {
                produtosAtacado.add(p);
            }
        }
        String[] vendedores = {"Carla Souza", "Davi", "Emilly"};

        for (int n = 0; n < 70; n++) {
            boolean atacado = sorteio.nextInt(100) < 22;           // ~22% das vendas são de atacado
            Cliente cliente = null;
            if (atacado) {
                cliente = clientes.get(4 + sorteio.nextInt(2));    // clientes atacadistas (posições 4 e 5)
            } else if (sorteio.nextInt(100) < 55) {
                cliente = clientes.get(sorteio.nextInt(4));        // clientes de varejo (posições 0 a 3)
            }

            // Sorteia de 1 a 3 produtos diferentes
            int quantosProdutos = 1 + sorteio.nextInt(3);
            Set<Integer> jaUsados = new HashSet<>();
            List<ItemVenda> itens = new ArrayList<>();
            for (int k = 0; k < quantosProdutos; k++) {
                List<Produto> opcoes = atacado ? produtosAtacado : produtos;
                Produto p = opcoes.get(sorteio.nextInt(opcoes.size()));
                if (!jaUsados.add(p.getId())) {
                    continue;                                       // não repete produto na mesma venda
                }
                int quantidade = atacado
                    ? quantidadesAtacado[sorteio.nextInt(quantidadesAtacado.length)]
                    : 1 + sorteio.nextInt(3);
                double preco = atacado ? p.getPrecoAtacado() : p.getPrecoVarejo();
                itens.add(new ItemVenda(p, quantidade, preco));
            }

            Venda v = new Venda();
            v.setTipo(atacado ? "ATACADO" : "VAREJO");
            v.setItens(itens.toArray(new ItemVenda[0]));            // lista -> VETOR
            v.recalcular(0);
            v.setClienteId(cliente == null ? 0 : cliente.getId());
            v.setClienteNome(cliente == null ? "Consumidor final" : cliente.getNome());
            v.setFormaPagamento(Venda.FORMAS_PAGAMENTO[sorteio.nextInt(Venda.FORMAS_PAGAMENTO.length)]);
            v.setVendedor(vendedores[sorteio.nextInt(vendedores.length)]);
            LocalDateTime quando = LocalDateTime.now()
                .minusDays(sorteio.nextInt(150))
                .withHour(9 + sorteio.nextInt(9)).withMinute(sorteio.nextInt(60)).withSecond(0).withNano(0);
            v.setData(quando.toString());
            geradas.add(v);
        }

        // Ordena por data (as datas ISO ordenam corretamente como texto) para os números das vendas ficarem em ordem cronológica
        geradas.sort((a, b) -> a.getData().compareTo(b.getData()));
        for (Venda v : geradas) {
            banco.vendas.salvar(v);
        }
    }

    // ---------------------------------------------------------------------
    //  Ordens de serviço
    // ---------------------------------------------------------------------
    private static void criarOrdens(Banco banco) {
        // MATRIZ: clienteId, nome (se avulso), aparelho, defeito, status, serviço, peças, dias atrás
        Object[][] linhas = {
            { 1, "",              "Smartphone 64GB Azul", "Tela trincada após queda",                      "Em reparo",       80.0, 189.9, 2 },
            { 2, "",              "Notebook 15 pol.",     "Não liga",                                     "Em análise",       0.0,   0.0, 1 },
            { 3, "",              "Smartphone 128GB",     "Bateria descarrega muito rápido",              "Pronta",          60.0,  69.9, 4 },
            { 4, "",              "PC Desktop",           "Formatação e instalação de programas",         "Entregue",       120.0,   0.0, 9 },
            { 1, "",              "Tablet 10 pol.",       "Não carrega (conector com defeito)",           "Aguardando peça", 70.0,  19.9, 3 },
            { 0, "Pedro Santos",  "Celular Básico",       "Alto-falante sem som",                         "Aberta",           0.0,   0.0, 0 },
            { 3, "",              "Notebook Gamer",       "Superaquecimento: limpeza e troca de pasta térmica", "Entregue",  150.0,   0.0, 12 },
        };
        for (Object[] l : linhas) {
            OrdemServico o = new OrdemServico();
            int clienteId = (Integer) l[0];
            o.setClienteId(clienteId);
            Cliente c = clienteId > 0 ? banco.clientes.buscar(clienteId) : null;
            o.setClienteNome(c != null ? c.getNome() : (String) l[1]);
            o.setAparelho((String) l[2]);
            o.setDefeito((String) l[3]);
            o.setStatus((String) l[4]);
            o.setValorServico((Double) l[5]);
            o.setValorPecas((Double) l[6]);
            o.setTecnico("Rafael Lima");
            LocalDateTime entrada = LocalDateTime.now().minusDays((Integer) l[7]).withNano(0);
            o.setDataEntrada(entrada.toString());
            if ("Entregue".equals(o.getStatus())) {
                o.setDataSaida(entrada.plusDays(2).toString());
            }
            banco.ordens.salvar(o);
        }
    }
}

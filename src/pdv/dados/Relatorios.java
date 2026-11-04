package pdv.dados;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pdv.modelo.Cliente;
import pdv.modelo.ItemVenda;
import pdv.modelo.OrdemServico;
import pdv.modelo.Produto;
import pdv.modelo.Venda;
import pdv.util.Seguranca;

/**
 * Cálculos do dashboard e dos relatórios gerenciais.
 *
 * Esta classe é a que mais usa VETORES e MATRIZES do projeto:
 *  - double[]   : totais por mês, por tipo de venda, lucro por categoria
 *  - int[]      : quantidade de OS por status
 *  - double[][] : matriz "categoria x forma de pagamento"
 *  - ordenação manual (selection sort) para montar rankings
 */
public final class Relatorios {

    private Relatorios() { }

    /** VETOR com os nomes abreviados dos meses (posição 0 = janeiro). */
    private static final String[] MESES = {
        "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    };

    /** Filtra apenas as vendas não canceladas. */
    private static List<Venda> concluidas(List<Venda> todas) {
        List<Venda> ok = new ArrayList<>();
        for (Venda v : todas) {
            if (v.concluida()) {
                ok.add(v);
            }
        }
        return ok;
    }

    /** Monta um mapa {rotulos: [...], valores: [...]} usado pelos gráficos do front end. */
    private static Map<String, Object> serie(String[] rotulos, double[] valores) {
        double[] arredondados = new double[valores.length];
        for (int i = 0; i < valores.length; i++) {
            arredondados[i] = Venda.arredondar(valores[i]);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rotulos", rotulos);
        m.put("valores", arredondados);
        return m;
    }

    // =====================================================================
    //  DASHBOARD
    // =====================================================================

    /** Gera todos os números da tela inicial (cartões, gráfico, rankings e alertas). */
    public static Map<String, Object> dashboard(List<Venda> vendas, List<Produto> produtos,
                                                List<Cliente> clientes, List<OrdemServico> ordens) {
        List<Venda> ok = concluidas(vendas);
        LocalDate hoje = LocalDate.now();
        YearMonth mesAtual = YearMonth.now();

        // ---- Cartões de resumo ----
        double totalHoje = 0, totalMes = 0, lucroMes = 0;
        int qtdHoje = 0, qtdMes = 0;
        for (Venda v : ok) {
            LocalDateTime data = v.getDataHora();
            if (data.toLocalDate().equals(hoje)) {
                totalHoje += v.getTotal();
                qtdHoje++;
            }
            if (YearMonth.from(data).equals(mesAtual)) {
                totalMes += v.getTotal();
                lucroMes += v.lucro();
                qtdMes++;
            }
        }
        double valorEstoque = 0;
        for (Produto p : produtos) {
            valorEstoque += p.getEstoque() * p.getPrecoCusto();
        }
        int osAbertas = 0;
        for (OrdemServico o : ordens) {
            if (o.emAberto()) {
                osAbertas++;
            }
        }

        Map<String, Object> cartoes = new LinkedHashMap<>();
        cartoes.put("vendasHoje", Venda.arredondar(totalHoje));
        cartoes.put("qtdVendasHoje", qtdHoje);
        cartoes.put("faturamentoMes", Venda.arredondar(totalMes));
        cartoes.put("qtdVendasMes", qtdMes);
        cartoes.put("ticketMedio", qtdMes == 0 ? 0 : Venda.arredondar(totalMes / qtdMes));
        cartoes.put("lucroMes", Venda.arredondar(lucroMes));
        cartoes.put("produtosCadastrados", produtos.size());
        cartoes.put("valorEstoque", Venda.arredondar(valorEstoque));
        cartoes.put("clientes", clientes.size());
        cartoes.put("osEmAberto", osAbertas);

        // ---- VETOR: faturamento dos últimos 6 meses ----
        String[] rotulos6 = new String[6];
        double[] valores6 = new double[6];
        for (int i = 0; i < 6; i++) {
            YearMonth mes = mesAtual.minusMonths(5 - i);      // do mais antigo ao mês atual
            rotulos6[i] = MESES[mes.getMonthValue() - 1];
            for (Venda v : ok) {
                if (YearMonth.from(v.getDataHora()).equals(mes)) {
                    valores6[i] += v.getTotal();
                }
            }
        }

        // ---- VETOR: quantidade de ordens de serviço por status ----
        double[] osPorStatus = new double[OrdemServico.STATUS.length];
        for (OrdemServico o : ordens) {
            int posicao = Seguranca.indice(OrdemServico.STATUS, o.getStatus());
            if (posicao >= 0) {
                osPorStatus[posicao]++;
            }
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("cartoes", cartoes);
        resposta.put("faturamento6Meses", serie(rotulos6, valores6));
        resposta.put("osPorStatus", serie(OrdemServico.STATUS, osPorStatus));
        resposta.put("maisVendidos", ranking(ok, 5));
        resposta.put("estoqueBaixo", estoqueBaixo(produtos, 8));
        return resposta;
    }

    // =====================================================================
    //  RELATÓRIOS GERENCIAIS
    // =====================================================================

    /** Gera os relatórios da tela "Relatórios". */
    public static Map<String, Object> completo(List<Venda> vendas) {
        List<Venda> ok = concluidas(vendas);
        String[] categorias = Produto.CATEGORIAS;
        String[] pagamentos = Venda.FORMAS_PAGAMENTO;

        // ---- MATRIZ: valor vendido por categoria (linhas) x forma de pagamento (colunas) ----
        double[][] matriz = new double[categorias.length][pagamentos.length];

        // ---- VETORES de apoio ----
        double[] lucroPorCategoria = new double[categorias.length];
        double[] porTipo = new double[Venda.TIPOS.length];     // [0] varejo, [1] atacado
        double[] mensal = new double[12];                       // janeiro a dezembro do ano atual
        int anoAtual = LocalDate.now().getYear();
        double totalVendido = 0, totalLucro = 0;

        for (Venda v : ok) {
            int coluna = Seguranca.indice(pagamentos, v.getFormaPagamento());
            for (ItemVenda item : v.getItens()) {
                int linha = Seguranca.indice(categorias, item.getCategoria());
                if (linha >= 0 && coluna >= 0) {
                    matriz[linha][coluna] += item.getSubtotal();          // acumula na célula [linha][coluna]
                }
                if (linha >= 0) {
                    lucroPorCategoria[linha] += item.getSubtotal() - item.getCustoUnitario() * item.getQuantidade();
                }
            }
            int tipo = Seguranca.indice(Venda.TIPOS, v.getTipo());
            if (tipo >= 0) {
                porTipo[tipo] += v.getTotal();
            }
            if (v.getDataHora().getYear() == anoAtual) {
                mensal[v.getDataHora().getMonthValue() - 1] += v.getTotal();
            }
            totalVendido += v.getTotal();
            totalLucro += v.lucro();
        }

        // Arredonda cada célula da matriz (percorre linhas e colunas com dois "for")
        for (int i = 0; i < matriz.length; i++) {
            for (int j = 0; j < matriz[i].length; j++) {
                matriz[i][j] = Venda.arredondar(matriz[i][j]);
            }
        }

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("totalVendido", Venda.arredondar(totalVendido));
        resumo.put("totalLucro", Venda.arredondar(totalLucro));
        resumo.put("qtdVendas", ok.size());
        resumo.put("ticketMedio", ok.isEmpty() ? 0 : Venda.arredondar(totalVendido / ok.size()));

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("resumo", resumo);
        resposta.put("categorias", categorias);
        resposta.put("pagamentos", pagamentos);
        resposta.put("matrizCategoriaPagamento", matriz);
        resposta.put("mensal", serie(MESES, mensal));
        resposta.put("porTipo", serie(new String[] {"Varejo", "Atacado"}, porTipo));
        resposta.put("lucroPorCategoria", serie(categorias, lucroPorCategoria));
        resposta.put("ranking", ranking(ok, 10));
        return resposta;
    }

    // =====================================================================
    //  RANKING DE PRODUTOS (com ordenação manual em vetores)
    // =====================================================================

    /**
     * Monta o ranking dos produtos mais vendidos (por quantidade).
     * Primeiro soma as vendas de cada produto, depois ordena usando o
     * algoritmo SELECTION SORT sobre vetores paralelos.
     */
    private static List<Map<String, Object>> ranking(List<Venda> vendas, int limite) {
        // 1) Soma quantidade e valor de cada produto (chave = nome do produto)
        Map<String, double[]> soma = new LinkedHashMap<>();
        for (Venda v : vendas) {
            for (ItemVenda item : v.getItens()) {
                double[] acumulado = soma.computeIfAbsent(item.getNome(), k -> new double[2]);
                acumulado[0] += item.getQuantidade();     // posição 0 = quantidade
                acumulado[1] += item.getSubtotal();       // posição 1 = valor vendido
            }
        }

        // 2) Copia para VETORES paralelos (nomes[i] corresponde a quantidades[i] e valores[i])
        int n = soma.size();
        String[] nomes = soma.keySet().toArray(new String[0]);
        double[] quantidades = new double[n];
        double[] valores = new double[n];
        for (int i = 0; i < n; i++) {
            quantidades[i] = soma.get(nomes[i])[0];
            valores[i] = soma.get(nomes[i])[1];
        }

        // 3) SELECTION SORT decrescente: a cada rodada, acha o maior e coloca na posição i
        for (int i = 0; i < n - 1; i++) {
            int maior = i;
            for (int j = i + 1; j < n; j++) {
                if (quantidades[j] > quantidades[maior]) {
                    maior = j;
                }
            }
            if (maior != i) {                              // troca as posições nos três vetores
                String tn = nomes[i];       nomes[i] = nomes[maior];             nomes[maior] = tn;
                double tq = quantidades[i]; quantidades[i] = quantidades[maior]; quantidades[maior] = tq;
                double tv = valores[i];     valores[i] = valores[maior];         valores[maior] = tv;
            }
        }

        // 4) Devolve apenas os primeiros "limite" colocados
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (int i = 0; i < Math.min(limite, n); i++) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("posicao", i + 1);
            linha.put("nome", nomes[i]);
            linha.put("quantidade", (int) quantidades[i]);
            linha.put("valor", Venda.arredondar(valores[i]));
            resultado.add(linha);
        }
        return resultado;
    }

    // =====================================================================
    //  ALERTA DE ESTOQUE BAIXO (ordenado com BUBBLE SORT)
    // =====================================================================

    /** Lista os produtos com estoque no mínimo ou abaixo dele, do mais crítico para o menos crítico. */
    private static List<Map<String, Object>> estoqueBaixo(List<Produto> produtos, int limite) {
        // Copia os produtos com estoque baixo para um VETOR
        List<Produto> baixos = new ArrayList<>();
        for (Produto p : produtos) {
            if (p.estoqueBaixo()) {
                baixos.add(p);
            }
        }
        Produto[] vetor = baixos.toArray(new Produto[0]);

        // BUBBLE SORT crescente pelo estoque: os menores sobem para o início
        for (int i = 0; i < vetor.length - 1; i++) {
            for (int j = 0; j < vetor.length - 1 - i; j++) {
                if (vetor[j].getEstoque() > vetor[j + 1].getEstoque()) {
                    Produto temp = vetor[j];
                    vetor[j] = vetor[j + 1];
                    vetor[j + 1] = temp;
                }
            }
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (int i = 0; i < Math.min(limite, vetor.length); i++) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("nome", vetor[i].getNome());
            linha.put("categoria", vetor[i].getCategoria());
            linha.put("estoque", vetor[i].getEstoque());
            linha.put("minimo", vetor[i].getEstoqueMinimo());
            resultado.add(linha);
        }
        return resultado;
    }
}

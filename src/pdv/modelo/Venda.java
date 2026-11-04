package pdv.modelo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pdv.util.Json;

/**
 * Venda registrada no PDV. Contém um VETOR de itens ({@link ItemVenda}[])
 * e sabe calcular os próprios totais, incluindo o desconto automático do atacado.
 */
public class Venda implements Entidade {

    // =====================================================================
    //  VETORES E MATRIZ DE REGRAS DE NEGÓCIO
    // =====================================================================

    /** VETOR com as formas de pagamento aceitas. */
    public static final String[] FORMAS_PAGAMENTO = {
        "Dinheiro", "Pix", "Cartão de Débito", "Cartão de Crédito", "Transferência"
    };

    /** VETOR com os tipos de venda. */
    public static final String[] TIPOS = {"VAREJO", "ATACADO"};

    /**
     * MATRIZ de desconto por volume (somente vendas de ATACADO).
     * Cada linha é uma faixa: { unidades mínimas na venda, % de desconto }.
     * Ex.: a partir de 30 unidades no total da venda, 5% de desconto.
     */
    public static final double[][] FAIXAS_ATACADO = {
        {  10, 3 },
        {  30, 5 },
        {  60, 8 },
        { 100, 12 }
    };

    /** Descobre o % de desconto percorrendo a matriz de faixas (vale a maior faixa atingida). */
    public static double percentualAtacado(int unidades) {
        double percentual = 0;
        for (double[] faixa : FAIXAS_ATACADO) {      // percorre as linhas da matriz
            if (unidades >= faixa[0]) {              // coluna 0 = unidades mínimas
                percentual = faixa[1];               // coluna 1 = percentual
            }
        }
        return percentual;
    }

    /** Arredonda para 2 casas decimais (centavos). */
    public static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    // ---- Atributos ----
    private int id;
    private String data = "";                 // data/hora no formato ISO: 2026-10-26T14:30:00
    private int clienteId;                    // 0 = consumidor final (sem cadastro)
    private String clienteNome = "Consumidor final";
    private String vendedor = "";
    private String tipo = TIPOS[0];
    private String formaPagamento = FORMAS_PAGAMENTO[0];
    private ItemVenda[] itens = new ItemVenda[0];   // VETOR de itens da venda
    private double subtotal;                  // soma dos itens
    private double percentualAtacado;         // % aplicado automaticamente (atacado)
    private double descontoAtacado;           // valor em R$ do desconto de atacado
    private double desconto;                  // desconto manual em R$ dado pelo operador
    private double total;                     // valor final cobrado
    private String status = "CONCLUIDA";      // CONCLUIDA ou CANCELADA

    /**
     * Calcula subtotal, descontos e total a partir dos itens.
     * É chamado tanto na simulação do carrinho quanto na venda real,
     * então a regra fica em um único lugar.
     *
     * @param descontoManual desconto em R$ digitado pelo operador (limitado ao valor da venda)
     */
    public void recalcular(double descontoManual) {
        double soma = 0;
        int unidades = 0;
        for (ItemVenda item : itens) {              // percorre o vetor de itens
            soma += item.getSubtotal();
            unidades += item.getQuantidade();
        }
        subtotal = arredondar(soma);
        percentualAtacado = TIPOS[1].equals(tipo) ? percentualAtacado(unidades) : 0;
        descontoAtacado = arredondar(subtotal * percentualAtacado / 100.0);
        double maximo = subtotal - descontoAtacado;          // desconto manual não pode passar disso
        desconto = arredondar(Math.max(0, Math.min(descontoManual, maximo)));
        total = arredondar(subtotal - descontoAtacado - desconto);
    }

    /** Lucro da venda = total recebido - custo dos produtos vendidos. */
    public double lucro() {
        double custo = 0;
        for (ItemVenda item : itens) {
            custo += item.getCustoUnitario() * item.getQuantidade();
        }
        return arredondar(total - custo);
    }

    /** @return true se a venda não foi cancelada. */
    public boolean concluida() {
        return "CONCLUIDA".equals(status);
    }

    /** Converte o texto da data em objeto de data/hora. */
    public LocalDateTime getDataHora() {
        return LocalDateTime.parse(data);
    }

    /** Reconstrói uma venda a partir de um Map (arquivo de dados). */
    public static Venda fromMap(Map<String, Object> m) {
        Venda v = new Venda();
        v.id = Json.inteiro(m, "id");
        v.data = Json.texto(m, "data");
        v.clienteId = Json.inteiro(m, "clienteId");
        v.clienteNome = Json.texto(m, "clienteNome");
        v.vendedor = Json.texto(m, "vendedor");
        v.tipo = Json.texto(m, "tipo");
        v.formaPagamento = Json.texto(m, "formaPagamento");
        v.subtotal = Json.numero(m, "subtotal");
        v.percentualAtacado = Json.numero(m, "percentualAtacado");
        v.descontoAtacado = Json.numero(m, "descontoAtacado");
        v.desconto = Json.numero(m, "desconto");
        v.total = Json.numero(m, "total");
        v.status = Json.texto(m, "status");

        // Converte a lista JSON de itens para o VETOR de ItemVenda
        List<Object> lista = Json.lista(m, "itens");
        v.itens = new ItemVenda[lista.size()];
        for (int i = 0; i < lista.size(); i++) {
            v.itens[i] = ItemVenda.fromMap(Json.mapa(lista.get(i)));
        }
        return v;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("data", data);
        m.put("clienteId", clienteId);
        m.put("clienteNome", clienteNome);
        m.put("vendedor", vendedor);
        m.put("tipo", tipo);
        m.put("formaPagamento", formaPagamento);
        m.put("subtotal", subtotal);
        m.put("percentualAtacado", percentualAtacado);
        m.put("descontoAtacado", descontoAtacado);
        m.put("desconto", desconto);
        m.put("total", total);
        m.put("status", status);
        Object[] listaItens = new Object[itens.length];
        for (int i = 0; i < itens.length; i++) {
            listaItens[i] = itens[i].toMap();
        }
        m.put("itens", listaItens);
        return m;
    }

    /** Vendas não são criadas por formulário genérico, mas mantemos o contrato da interface. */
    @Override
    public void validar() {
        if (itens.length == 0) {
            throw new IllegalArgumentException("A venda precisa ter ao menos um item.");
        }
    }

    // ---- Getters e Setters ----
    @Override public int getId() { return id; }
    @Override public void setId(int id) { this.id = id; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public int getClienteId() { return clienteId; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }
    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }
    public String getVendedor() { return vendedor; }
    public void setVendedor(String vendedor) { this.vendedor = vendedor; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(String formaPagamento) { this.formaPagamento = formaPagamento; }
    public ItemVenda[] getItens() { return itens; }
    public void setItens(ItemVenda[] itens) { this.itens = itens; }
    public double getSubtotal() { return subtotal; }
    public double getPercentualAtacado() { return percentualAtacado; }
    public double getDescontoAtacado() { return descontoAtacado; }
    public double getDesconto() { return desconto; }
    public double getTotal() { return total; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

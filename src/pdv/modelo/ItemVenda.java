package pdv.modelo;

import java.util.LinkedHashMap;
import java.util.Map;
import pdv.util.Json;

/**
 * Uma linha da venda: "3 x Capinha Silicone".
 * Guardamos uma "fotografia" (nome, categoria, preços) do produto no momento da venda,
 * assim o histórico continua correto mesmo que o produto seja alterado ou excluído depois.
 */
public class ItemVenda {

    // ---- Atributos ----
    private int produtoId;
    private String nome = "";
    private String categoria = "";
    private int quantidade;
    private double custoUnitario;   // custo do produto na hora da venda (para calcular o lucro)
    private double precoUnitario;   // preço cobrado (varejo ou atacado)
    private double subtotal;        // quantidade x preço unitário

    /** Construtor vazio (usado ao carregar do arquivo). */
    public ItemVenda() { }

    /** Cria um item a partir de um produto e da quantidade comprada. */
    public ItemVenda(Produto p, int quantidade, double precoUnitario) {
        this.produtoId = p.getId();
        this.nome = p.getNome();
        this.categoria = p.getCategoria();
        this.quantidade = quantidade;
        this.custoUnitario = p.getPrecoCusto();
        this.precoUnitario = precoUnitario;
        this.subtotal = Venda.arredondar(quantidade * precoUnitario);
    }

    /** Reconstrói um item a partir de um Map (arquivo de dados). */
    public static ItemVenda fromMap(Map<String, Object> m) {
        ItemVenda i = new ItemVenda();
        i.produtoId = Json.inteiro(m, "produtoId");
        i.nome = Json.texto(m, "nome");
        i.categoria = Json.texto(m, "categoria");
        i.quantidade = Json.inteiro(m, "quantidade");
        i.custoUnitario = Json.numero(m, "custoUnitario");
        i.precoUnitario = Json.numero(m, "precoUnitario");
        i.subtotal = Json.numero(m, "subtotal");
        return i;
    }

    /** Converte o item em Map (para gravar em arquivo ou enviar ao navegador). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("produtoId", produtoId);
        m.put("nome", nome);
        m.put("categoria", categoria);
        m.put("quantidade", quantidade);
        m.put("custoUnitario", custoUnitario);
        m.put("precoUnitario", precoUnitario);
        m.put("subtotal", subtotal);
        return m;
    }

    // ---- Getters e Setters ----
    public int getProdutoId() { return produtoId; }
    public String getNome() { return nome; }
    public String getCategoria() { return categoria; }
    public int getQuantidade() { return quantidade; }
    public double getCustoUnitario() { return custoUnitario; }
    public double getPrecoUnitario() { return precoUnitario; }
    public double getSubtotal() { return subtotal; }
}

package pdv.modelo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import pdv.util.Json;

/**
 * Produto vendido na loja (aparelhos, acessórios, peças, hardware, software, periféricos).
 * Cada produto tem três preços: custo (quanto pagamos), varejo e atacado.
 */
public class Produto implements Entidade {

    /** VETOR com as categorias de produto da loja. */
    public static final String[] CATEGORIAS = {
        "Aparelhos", "Acessórios", "Peças (Atacado)", "Hardware", "Software", "Periféricos"
    };

    // ---- Atributos ----
    private int id;
    private String codigo = "";        // código interno / código de barras
    private String nome = "";
    private String categoria = CATEGORIAS[0];
    private int fornecedorId;          // 0 = sem fornecedor definido
    private double precoCusto;         // quanto a loja paga
    private double precoVarejo;        // preço para o consumidor final
    private double precoAtacado;       // preço para lojistas / técnicos
    private int estoque;               // quantidade disponível
    private int estoqueMinimo;         // abaixo (ou igual) disso, gera alerta de reposição

    /** Cria um produto a partir de um Map (arquivo de dados ou formulário do navegador). */
    public static Produto fromMap(Map<String, Object> m) {
        Produto p = new Produto();
        p.id = Json.inteiro(m, "id");
        p.codigo = Json.texto(m, "codigo");
        p.nome = Json.texto(m, "nome");
        p.categoria = Json.texto(m, "categoria");
        p.fornecedorId = Json.inteiro(m, "fornecedorId");
        p.precoCusto = Json.numero(m, "precoCusto");
        p.precoVarejo = Json.numero(m, "precoVarejo");
        p.precoAtacado = Json.numero(m, "precoAtacado");
        p.estoque = Json.inteiro(m, "estoque");
        p.estoqueMinimo = Json.inteiro(m, "estoqueMinimo");
        return p;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("codigo", codigo);
        m.put("nome", nome);
        m.put("categoria", categoria);
        m.put("fornecedorId", fornecedorId);
        m.put("precoCusto", precoCusto);
        m.put("precoVarejo", precoVarejo);
        m.put("precoAtacado", precoAtacado);
        m.put("estoque", estoque);
        m.put("estoqueMinimo", estoqueMinimo);
        return m;
    }

    /** Na API, acrescentamos um campo calculado indicando se o estoque está baixo. */
    @Override
    public Map<String, Object> paraApi() {
        Map<String, Object> m = toMap();
        m.put("estoqueBaixo", estoqueBaixo());
        return m;
    }

    @Override
    public void validar() {
        if (nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do produto.");
        }
        if (codigo.isBlank()) {
            throw new IllegalArgumentException("Informe o código do produto.");
        }
        if (!Arrays.asList(CATEGORIAS).contains(categoria)) {
            throw new IllegalArgumentException("Categoria inválida.");
        }
        if (precoCusto < 0 || precoVarejo < 0 || precoAtacado < 0) {
            throw new IllegalArgumentException("Os preços não podem ser negativos.");
        }
        if (precoVarejo <= 0) {
            throw new IllegalArgumentException("Informe o preço de varejo (maior que zero).");
        }
        if (estoque < 0 || estoqueMinimo < 0) {
            throw new IllegalArgumentException("Estoque e estoque mínimo não podem ser negativos.");
        }
    }

    /** @return true quando o estoque chegou ao mínimo (hora de repor). */
    public boolean estoqueBaixo() {
        return estoque <= estoqueMinimo;
    }

    // ---- Getters e Setters ----
    @Override public int getId() { return id; }
    @Override public void setId(int id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public int getFornecedorId() { return fornecedorId; }
    public void setFornecedorId(int fornecedorId) { this.fornecedorId = fornecedorId; }
    public double getPrecoCusto() { return precoCusto; }
    public void setPrecoCusto(double precoCusto) { this.precoCusto = precoCusto; }
    public double getPrecoVarejo() { return precoVarejo; }
    public void setPrecoVarejo(double precoVarejo) { this.precoVarejo = precoVarejo; }
    public double getPrecoAtacado() { return precoAtacado; }
    public void setPrecoAtacado(double precoAtacado) { this.precoAtacado = precoAtacado; }
    public int getEstoque() { return estoque; }
    public void setEstoque(int estoque) { this.estoque = estoque; }
    public int getEstoqueMinimo() { return estoqueMinimo; }
    public void setEstoqueMinimo(int estoqueMinimo) { this.estoqueMinimo = estoqueMinimo; }
}

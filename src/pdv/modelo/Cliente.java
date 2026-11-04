package pdv.modelo;

import java.util.LinkedHashMap;
import java.util.Map;
import pdv.util.Json;

/**
 * Cliente da loja. Pode ser de VAREJO (consumidor final) ou ATACADO
 * (lojistas, técnicos e empresas que compram peças em quantidade).
 */
public class Cliente implements Entidade {

    /** VETOR com os tipos de cliente aceitos. */
    public static final String[] TIPOS = {"VAREJO", "ATACADO"};

    // ---- Atributos ----
    private int id;
    private String nome = "";
    private String documento = "";     // CPF ou CNPJ
    private String telefone = "";
    private String email = "";
    private String endereco = "";
    private String tipo = TIPOS[0];
    private String observacoes = "";

    /** Cria um cliente a partir de um Map. */
    public static Cliente fromMap(Map<String, Object> m) {
        Cliente c = new Cliente();
        c.id = Json.inteiro(m, "id");
        c.nome = Json.texto(m, "nome");
        c.documento = Json.texto(m, "documento");
        c.telefone = Json.texto(m, "telefone");
        c.email = Json.texto(m, "email");
        c.endereco = Json.texto(m, "endereco");
        c.tipo = Json.texto(m, "tipo").toUpperCase();
        c.observacoes = Json.texto(m, "observacoes");
        if (c.tipo.isEmpty()) {
            c.tipo = TIPOS[0];     // padrão: varejo
        }
        return c;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nome", nome);
        m.put("documento", documento);
        m.put("telefone", telefone);
        m.put("email", email);
        m.put("endereco", endereco);
        m.put("tipo", tipo);
        m.put("observacoes", observacoes);
        return m;
    }

    @Override
    public void validar() {
        if (nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do cliente.");
        }
        if (!tipo.equals(TIPOS[0]) && !tipo.equals(TIPOS[1])) {
            throw new IllegalArgumentException("Tipo de cliente inválido.");
        }
        if (!email.isBlank() && !email.contains("@")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
    }

    // ---- Getters e Setters ----
    @Override public int getId() { return id; }
    @Override public void setId(int id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
}

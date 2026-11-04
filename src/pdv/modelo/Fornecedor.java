package pdv.modelo;

import java.util.LinkedHashMap;
import java.util.Map;
import pdv.util.Json;

/**
 * Fornecedor: empresa de quem a loja compra produtos para revender.
 */
public class Fornecedor implements Entidade {

    // ---- Atributos ----
    private int id;
    private String razaoSocial = "";
    private String documento = "";          // CNPJ
    private String contato = "";            // nome da pessoa de contato
    private String telefone = "";
    private String email = "";
    private String produtosFornecidos = ""; // descrição livre do que ele fornece

    /** Cria um fornecedor a partir de um Map. */
    public static Fornecedor fromMap(Map<String, Object> m) {
        Fornecedor f = new Fornecedor();
        f.id = Json.inteiro(m, "id");
        f.razaoSocial = Json.texto(m, "razaoSocial");
        f.documento = Json.texto(m, "documento");
        f.contato = Json.texto(m, "contato");
        f.telefone = Json.texto(m, "telefone");
        f.email = Json.texto(m, "email");
        f.produtosFornecidos = Json.texto(m, "produtosFornecidos");
        return f;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("razaoSocial", razaoSocial);
        m.put("documento", documento);
        m.put("contato", contato);
        m.put("telefone", telefone);
        m.put("email", email);
        m.put("produtosFornecidos", produtosFornecidos);
        return m;
    }

    @Override
    public void validar() {
        if (razaoSocial.isBlank()) {
            throw new IllegalArgumentException("Informe a razão social / nome do fornecedor.");
        }
        if (!email.isBlank() && !email.contains("@")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
    }

    // ---- Getters e Setters ----
    @Override public int getId() { return id; }
    @Override public void setId(int id) { this.id = id; }
    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String razaoSocial) { this.razaoSocial = razaoSocial; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getContato() { return contato; }
    public void setContato(String contato) { this.contato = contato; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getProdutosFornecidos() { return produtosFornecidos; }
    public void setProdutosFornecidos(String produtosFornecidos) { this.produtosFornecidos = produtosFornecidos; }
}

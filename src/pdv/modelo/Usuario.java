package pdv.modelo;

import java.util.Map;
import java.util.LinkedHashMap;
import pdv.util.Json;
import pdv.util.Seguranca;

/**
 * Usuário do sistema (quem faz login). O perfil define o que ele pode acessar
 * (veja a matriz de permissões em {@link Seguranca}).
 */
public class Usuario implements Entidade {

    // ---- Atributos ----
    private int id;
    private String nome = "";
    private String login = "";
    private String senhaHash = "";   // NUNCA guardamos a senha em texto puro, só o hash
    private String perfil = "VENDEDOR";
    private boolean ativo = true;

    /**
     * Cria um usuário a partir de um Map.
     * - Vindo do ARQUIVO: traz "senhaHash" pronto.
     * - Vindo do FORMULÁRIO: traz "senha" em texto; aqui ela já é convertida em hash.
     */
    public static Usuario fromMap(Map<String, Object> m) {
        Usuario u = new Usuario();
        u.id = Json.inteiro(m, "id");
        u.nome = Json.texto(m, "nome");
        u.login = Json.texto(m, "login").toLowerCase();
        u.perfil = Json.texto(m, "perfil").toUpperCase();
        u.ativo = Json.booleano(m, "ativo", true);
        u.senhaHash = Json.texto(m, "senhaHash");
        if (u.perfil.isEmpty()) {
            u.perfil = "VENDEDOR";
        }

        // Senha digitada no formulário (não usamos trim: espaços podem fazer parte da senha)
        Object senhaDigitada = m.get("senha");
        if (senhaDigitada != null && !String.valueOf(senhaDigitada).isEmpty()) {
            String senha = String.valueOf(senhaDigitada);
            if (senha.length() < 4) {
                throw new IllegalArgumentException("A senha deve ter pelo menos 4 caracteres.");
            }
            u.senhaHash = Seguranca.gerarHash(senha);
        }
        return u;
    }

    /** Versão completa (com hash), usada apenas para gravar no arquivo de dados. */
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nome", nome);
        m.put("login", login);
        m.put("senhaHash", senhaHash);
        m.put("perfil", perfil);
        m.put("ativo", ativo);
        return m;
    }

    /** Versão enviada ao navegador: SEM o hash da senha. */
    @Override
    public Map<String, Object> paraApi() {
        Map<String, Object> m = toMap();
        m.remove("senhaHash");
        return m;
    }

    @Override
    public void validar() {
        if (nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do usuário.");
        }
        if (!login.matches("[a-z0-9._-]{3,}")) {
            throw new IllegalArgumentException("O login deve ter ao menos 3 caracteres (letras, números, ponto, hífen ou _).");
        }
        if (Seguranca.indice(Seguranca.PERFIS, perfil) < 0) {
            throw new IllegalArgumentException("Perfil inválido.");
        }
    }

    // ---- Getters e Setters ----
    @Override public int getId() { return id; }
    @Override public void setId(int id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}

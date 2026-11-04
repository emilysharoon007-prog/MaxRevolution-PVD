package pdv.dados;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pdv.util.Json;

/**
 * Guarda os registros em um banco de dados PostgreSQL (usado quando o sistema
 * está publicado na internet), através do JDBC.
 *
 * Cada cadastro vira uma tabela simples com duas colunas:
 *   id    INTEGER PRIMARY KEY   -> identificador do registro
 *   dados TEXT                  -> o registro inteiro em JSON
 * Assim todos os cadastros usam o mesmo código, sem precisar de uma tabela
 * diferente para cada um.
 *
 * Ao gravar, só os registros novos, alterados ou removidos são enviados ao banco
 * (comparando com o que já estava guardado), em uma única transação.
 */
public class ArmazenamentoBanco implements Armazenamento {

    private final String url;       // endereço JDBC: jdbc:postgresql://servidor/banco?...
    private final String usuario;
    private final String senha;
    private final String tabela;    // nome da tabela deste cadastro

    /** Último conteúdo conhecido do banco: id -> JSON (serve para descobrir o que mudou). */
    private final Map<Integer, String> conhecido = new HashMap<>();

    /**
     * @param databaseUrl endereço no formato que Render/Neon/Supabase fornecem:
     *                    postgresql://usuario:senha@servidor/banco  (ou jdbc:postgresql://...)
     * @param tabela      nome da tabela (ex.: "produtos")
     */
    public ArmazenamentoBanco(String databaseUrl, String tabela) {
        this.tabela = "pdv_" + tabela.replaceAll("[^a-zA-Z0-9_]", "");   // evita caracteres perigosos no nome
        String[] partes = converterUrl(databaseUrl);
        this.url = partes[0];
        this.usuario = partes[1];
        this.senha = partes[2];
        try (Connection c = abrir(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + this.tabela
                     + " (id INTEGER PRIMARY KEY, dados TEXT NOT NULL)");
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível preparar a tabela " + this.tabela
                                          + " no banco: " + e.getMessage(), e);
        }
    }

    /**
     * Converte o endereço fornecido pela hospedagem em { urlJdbc, usuario, senha }.
     * Aceita "postgresql://usuario:senha@servidor/banco?parametros" e também o formato "jdbc:postgresql://...".
     * Usuário e senha ficam nulos quando já estão dentro de um endereço jdbc:.
     */
    static String[] converterUrl(String databaseUrl) {
        String bruto = databaseUrl.trim();
        if (bruto.startsWith("jdbc:")) {
            return new String[] {bruto, null, null};
        }
        URI uri = URI.create(bruto.replaceFirst("^postgres(ql)?://", "postgresql://"));
        String[] credenciais = uri.getRawUserInfo() == null ? new String[] {"", ""} : uri.getRawUserInfo().split(":", 2);
        String usuario = decodificar(credenciais[0]);
        String senha = credenciais.length > 1 ? decodificar(credenciais[1]) : "";
        int porta = uri.getPort() > 0 ? uri.getPort() : 5432;
        String consulta = uri.getRawQuery() == null ? "sslmode=require" : uri.getRawQuery();
        String url = "jdbc:postgresql://" + uri.getHost() + ":" + porta + uri.getPath() + "?" + consulta;
        return new String[] {url, usuario, senha};
    }

    /** Desfaz a codificação de URL (ex.: %40 vira @) nas credenciais. */
    private static String decodificar(String texto) {
        return java.net.URLDecoder.decode(texto, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Abre uma conexão nova (simples e segura contra conexões que o servidor derrubou por inatividade). */
    private Connection abrir() throws SQLException {
        return usuario == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, usuario, senha);
    }

    @Override
    public synchronized List<Map<String, Object>> ler() {
        List<Map<String, Object>> registros = new ArrayList<>();
        conhecido.clear();
        try (Connection c = abrir();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, dados FROM " + tabela + " ORDER BY id")) {
            while (rs.next()) {
                String json = rs.getString("dados");
                conhecido.put(rs.getInt("id"), json);
                registros.add(Json.mapa(Json.parse(json)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao ler a tabela " + tabela + ": " + e.getMessage(), e);
        }
        return registros;
    }

    @Override
    public synchronized void gravar(List<Map<String, Object>> registros) {
        // 1) Descobre o que mudou em relação ao que já está no banco
        Map<Integer, String> novoEstado = new HashMap<>();
        for (Map<String, Object> m : registros) {
            novoEstado.put(((Number) m.get("id")).intValue(), Json.stringify(m));
        }
        try (Connection c = abrir()) {
            c.setAutoCommit(false);                       // tudo ou nada
            try {
                // 2) Insere/atualiza os registros novos ou alterados
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + tabela + " (id, dados) VALUES (?, ?) "
                      + "ON CONFLICT (id) DO UPDATE SET dados = EXCLUDED.dados")) {
                    for (Map.Entry<Integer, String> e : novoEstado.entrySet()) {
                        if (!e.getValue().equals(conhecido.get(e.getKey()))) {
                            ps.setInt(1, e.getKey());
                            ps.setString(2, e.getValue());
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
                // 3) Remove os registros que não existem mais
                Set<Integer> removidos = new HashSet<>(conhecido.keySet());
                removidos.removeAll(novoEstado.keySet());
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tabela + " WHERE id = ?")) {
                    for (Integer id : removidos) {
                        ps.setInt(1, id);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();                             // deu erro: desfaz tudo
                throw e;
            }
            conhecido.clear();
            conhecido.putAll(novoEstado);
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao gravar na tabela " + tabela + ": " + e.getMessage(), e);
        }
    }
}

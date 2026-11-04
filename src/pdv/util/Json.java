package pdv.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mini biblioteca de JSON escrita do zero (não usamos nenhuma biblioteca externa).
 *
 * <ul>
 *   <li>{@link #parse(String)}     : texto JSON  -> objetos Java (Map, List, String, Long, Double, Boolean, null)</li>
 *   <li>{@link #stringify(Object)} : objetos Java -> texto JSON (aceita Map, List, vetores e matrizes)</li>
 * </ul>
 *
 * Também traz métodos auxiliares ({@link #texto}, {@link #numero}...) para ler
 * valores de um Map de forma segura.
 */
public final class Json {

    /** Texto JSON que está sendo lido. */
    private final String txt;
    /** Posição atual de leitura dentro do texto. */
    private int pos = 0;

    private Json(String txt) {
        this.txt = txt;
    }

    // =====================================================================
    //  LEITURA (PARSE)
    // =====================================================================

    /**
     * Converte um texto JSON em objetos Java.
     * Objetos viram {@code Map<String,Object>}, listas viram {@code List<Object>}.
     *
     * @throws IllegalArgumentException se o texto não for um JSON válido
     */
    public static Object parse(String texto) {
        Json leitor = new Json(texto);
        leitor.pularEspacos();
        Object valor = leitor.lerValor();
        leitor.pularEspacos();
        if (leitor.pos != leitor.txt.length()) {
            throw leitor.erro("conteúdo inesperado após o fim do JSON");
        }
        return valor;
    }

    /** Cria a exceção padrão de JSON inválido, indicando a posição do problema. */
    private IllegalArgumentException erro(String mensagem) {
        return new IllegalArgumentException("JSON inválido (posição " + pos + "): " + mensagem);
    }

    /** Avança a posição ignorando espaços, tabs e quebras de linha. */
    private void pularEspacos() {
        while (pos < txt.length() && Character.isWhitespace(txt.charAt(pos))) {
            pos++;
        }
    }

    /** Devolve o caractere atual (e falha se o texto acabou). */
    private char atual() {
        if (pos >= txt.length()) {
            throw erro("fim inesperado do texto");
        }
        return txt.charAt(pos);
    }

    /** Lê qualquer valor JSON, decidindo o tipo pelo primeiro caractere. */
    private Object lerValor() {
        char c = atual();
        switch (c) {
            case '{': return lerObjeto();
            case '[': return lerLista();
            case '"': return lerTexto();
            case 't': consumirPalavra("true");  return Boolean.TRUE;
            case 'f': consumirPalavra("false"); return Boolean.FALSE;
            case 'n': consumirPalavra("null");  return null;
            default:  return lerNumero();
        }
    }

    /** Confirma que o texto contém exatamente a palavra esperada (true/false/null). */
    private void consumirPalavra(String palavra) {
        if (!txt.startsWith(palavra, pos)) {
            throw erro("esperado '" + palavra + "'");
        }
        pos += palavra.length();
    }

    /** Lê um objeto JSON { "chave": valor, ... } preservando a ordem das chaves. */
    private Map<String, Object> lerObjeto() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        pos++; // pula o '{'
        pularEspacos();
        if (atual() == '}') {   // objeto vazio
            pos++;
            return mapa;
        }
        while (true) {
            pularEspacos();
            if (atual() != '"') {
                throw erro("esperada uma chave entre aspas");
            }
            String chave = lerTexto();
            pularEspacos();
            if (atual() != ':') {
                throw erro("esperado ':' depois da chave");
            }
            pos++;
            pularEspacos();
            mapa.put(chave, lerValor());
            pularEspacos();
            char c = atual();
            pos++;
            if (c == '}') {
                return mapa;
            }
            if (c != ',') {
                throw erro("esperado ',' ou '}'");
            }
        }
    }

    /** Lê uma lista JSON [ valor, valor, ... ]. */
    private List<Object> lerLista() {
        List<Object> lista = new ArrayList<>();
        pos++; // pula o '['
        pularEspacos();
        if (atual() == ']') {   // lista vazia
            pos++;
            return lista;
        }
        while (true) {
            pularEspacos();
            lista.add(lerValor());
            pularEspacos();
            char c = atual();
            pos++;
            if (c == ']') {
                return lista;
            }
            if (c != ',') {
                throw erro("esperado ',' ou ']'");
            }
        }
    }

    /** Lê um texto entre aspas, tratando as sequências de escape (quebra de linha, aspas, código unicode...). */
    private String lerTexto() {
        StringBuilder sb = new StringBuilder();
        pos++; // pula a aspa de abertura
        while (true) {
            char c = atual();
            pos++;
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = atual();   // caractere depois da barra invertida
            pos++;
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case '"': sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/');  break;
                case 'u':
                    if (pos + 4 > txt.length()) {
                        throw erro("sequência \\u incompleta");
                    }
                    sb.append((char) Integer.parseInt(txt.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    throw erro("sequência de escape inválida");
            }
        }
    }

    /** Lê um número: inteiros viram Long, com casas decimais/expoente viram Double. */
    private Object lerNumero() {
        int inicio = pos;
        while (pos < txt.length() && "+-0123456789.eE".indexOf(txt.charAt(pos)) >= 0) {
            pos++;
        }
        String numero = txt.substring(inicio, pos);
        if (numero.isEmpty()) {
            throw erro("valor inesperado '" + txt.charAt(pos) + "'");
        }
        try {
            if (numero.matches("-?\\d+")) {
                return Long.parseLong(numero);
            }
            return Double.parseDouble(numero);
        } catch (NumberFormatException ex) {
            pos = inicio;
            throw erro("número inválido '" + numero + "'");
        }
    }

    // =====================================================================
    //  ESCRITA (STRINGIFY)
    // =====================================================================

    /** Converte um objeto Java em JSON compacto (usado nas respostas da API). */
    public static String stringify(Object objeto) {
        StringBuilder sb = new StringBuilder();
        escrever(sb, objeto, false, 0);
        return sb.toString();
    }

    /** Converte um objeto Java em JSON indentado (usado nos arquivos de dados, mais legíveis). */
    public static String bonito(Object objeto) {
        StringBuilder sb = new StringBuilder();
        escrever(sb, objeto, true, 0);
        return sb.toString();
    }

    /** Escreve recursivamente qualquer valor Java no formato JSON. */
    private static void escrever(StringBuilder sb, Object o, boolean bonito, int nivel) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            escreverTexto(sb, (String) o);
        } else if (o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if ((o instanceof Double || o instanceof Float) && (Double.isNaN(d) || Double.isInfinite(d))) {
                sb.append("null");           // JSON não aceita NaN/Infinito
            } else {
                sb.append(o);
            }
        } else if (o instanceof Map) {
            Map<?, ?> mapa = (Map<?, ?>) o;
            if (mapa.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                quebra(sb, bonito, nivel + 1);
                escreverTexto(sb, String.valueOf(e.getKey()));
                sb.append(bonito ? ": " : ":");
                escrever(sb, e.getValue(), bonito, nivel + 1);
            }
            quebra(sb, bonito, nivel);
            sb.append('}');
        } else if (o instanceof Collection) {
            Collection<?> colecao = (Collection<?>) o;
            if (colecao.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean primeiro = true;
            for (Object item : colecao) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                quebra(sb, bonito, nivel + 1);
                escrever(sb, item, bonito, nivel + 1);
            }
            quebra(sb, bonito, nivel);
            sb.append(']');
        } else if (o.getClass().isArray()) {
            // Vetores e matrizes (int[], double[], String[], double[][]...) viram listas JSON.
            List<Object> lista = new ArrayList<>();
            for (int i = 0; i < Array.getLength(o); i++) {
                lista.add(Array.get(o, i));   // para matrizes, cada linha é outro vetor
            }
            escrever(sb, lista, bonito, nivel);
        } else {
            escreverTexto(sb, String.valueOf(o));
        }
    }

    /** Insere quebra de linha + indentação (somente no modo "bonito"). */
    private static void quebra(StringBuilder sb, boolean bonito, int nivel) {
        if (!bonito) {
            return;
        }
        sb.append('\n');
        for (int i = 0; i < nivel; i++) {
            sb.append("  ");
        }
    }

    /** Escreve um texto entre aspas aplicando o escape dos caracteres especiais. */
    private static void escreverTexto(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // =====================================================================
    //  AUXILIARES PARA LER CAMPOS DE UM MAP
    // =====================================================================

    /** Garante que o objeto recebido é um objeto JSON (Map); caso contrário, gera erro 400. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapa(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        throw new IllegalArgumentException("Corpo da requisição inválido: era esperado um objeto JSON.");
    }

    /** Lê um campo como texto (sem espaços nas pontas). Campo ausente vira "". */
    public static String texto(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** Lê um campo como número decimal. Aceita número JSON ou texto ("12,50"). Ausente vira 0. */
    public static double numero(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof String && !((String) v).isBlank()) {
            try {
                return Double.parseDouble(((String) v).trim().replace(',', '.'));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Valor numérico inválido no campo '" + chave + "'.");
            }
        }
        return 0;
    }

    /** Lê um campo como número inteiro (arredondado). */
    public static int inteiro(Map<String, Object> m, String chave) {
        return (int) Math.round(numero(m, chave));
    }

    /** Lê um campo verdadeiro/falso; se não existir, devolve o valor padrão. */
    public static boolean booleano(Map<String, Object> m, String chave, boolean padrao) {
        Object v = m.get(chave);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            return ((String) v).equalsIgnoreCase("true");
        }
        return padrao;
    }

    /** Lê um campo como lista; se não existir, devolve uma lista vazia. */
    @SuppressWarnings("unchecked")
    public static List<Object> lista(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }
}

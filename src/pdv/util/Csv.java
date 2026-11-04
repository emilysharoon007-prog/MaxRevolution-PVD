package pdv.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Leitura e escrita de arquivos CSV (valores separados por ponto e vírgula).
 *
 * Escolhemos o ";" como separador porque é o padrão do Excel em português
 * (a vírgula é usada nos números decimais). Assim, qualquer arquivo da pasta
 * "dados" pode ser aberto no Excel para conferência.
 *
 * Regras do formato (RFC 4180):
 *  - um campo que contém ";", aspas ou quebra de linha vai entre aspas;
 *  - aspas dentro de um campo são escritas duplicadas ("").
 */
public final class Csv {

    private Csv() { }

    /** Separador de colunas. */
    public static final char SEPARADOR = ';';

    // =====================================================================
    //  ESCRITA
    // =====================================================================

    /** Converte um campo em texto seguro para CSV (coloca aspas só quando necessário). */
    public static String escapar(String campo) {
        if (campo == null) {
            return "";
        }
        boolean precisaAspas = campo.indexOf(SEPARADOR) >= 0 || campo.indexOf('"') >= 0
                            || campo.indexOf('\n') >= 0 || campo.indexOf('\r') >= 0;
        if (!precisaAspas) {
            return campo;
        }
        return "\"" + campo.replace("\"", "\"\"") + "\"";
    }

    /** Monta uma linha de CSV a partir de uma lista de campos. */
    public static String linha(List<String> campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                sb.append(SEPARADOR);
            }
            sb.append(escapar(campos.get(i)));
        }
        return sb.toString();
    }

    // =====================================================================
    //  LEITURA
    // =====================================================================

    /**
     * Lê o texto inteiro de um CSV e devolve uma lista de linhas, cada uma
     * com a lista dos seus campos. Funciona com campos entre aspas, inclusive
     * os que contêm quebras de linha.
     */
    public static List<List<String>> ler(String texto) {
        List<List<String>> linhas = new ArrayList<>();
        List<String> linhaAtual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;

        // Remove o BOM (marca invisível que alguns programas colocam no início do arquivo)
        if (!texto.isEmpty() && texto.charAt(0) == '﻿') {
            texto = texto.substring(1);
        }

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreAspas) {
                if (c == '"') {
                    boolean aspasDuplas = i + 1 < texto.length() && texto.charAt(i + 1) == '"';
                    if (aspasDuplas) {              // "" dentro de um campo = uma aspa literal
                        campo.append('"');
                        i++;
                    } else {
                        entreAspas = false;         // fim do trecho entre aspas
                    }
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreAspas = true;
            } else if (c == SEPARADOR) {
                linhaAtual.add(campo.toString());   // fim de um campo
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;                            // trata \r\n (Windows) como uma única quebra
                }
                linhaAtual.add(campo.toString());   // fim da linha
                campo.setLength(0);
                linhas.add(linhaAtual);
                linhaAtual = new ArrayList<>();
            } else {
                campo.append(c);
            }
        }
        // Última linha (quando o arquivo não termina com quebra de linha)
        if (campo.length() > 0 || !linhaAtual.isEmpty()) {
            linhaAtual.add(campo.toString());
            linhas.add(linhaAtual);
        }
        // Descarta linhas totalmente vazias
        linhas.removeIf(l -> l.size() == 1 && l.get(0).isEmpty());
        return linhas;
    }
}

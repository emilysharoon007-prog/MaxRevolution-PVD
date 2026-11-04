package pdv.dados;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pdv.util.Csv;
import pdv.util.Json;

/**
 * Guarda os registros em um arquivo CSV (separado por ";", abre no Excel).
 *
 * Formato: a 1ª linha traz os nomes das colunas e cada linha seguinte é um
 * registro. Valores simples (texto, número, verdadeiro/falso) ficam direto na
 * célula. Valores "aninhados" (como a lista de itens de uma venda) ficam em uma
 * única célula com o prefixo "@json:" seguido do JSON.
 */
public class ArmazenamentoCsv implements Armazenamento {

    /** Prefixo que marca uma célula que contém uma estrutura (lista/mapa) em JSON. */
    private static final String PREFIXO_JSON = "@json:";

    private final Path arquivo;

    public ArmazenamentoCsv(Path arquivo) {
        this.arquivo = arquivo;
    }

    /** Lê o arquivo CSV (se existir) e devolve um Map por linha. */
    @Override
    public List<Map<String, Object>> ler() {
        List<Map<String, Object>> registros = new ArrayList<>();
        if (!Files.exists(arquivo)) {
            return registros;                  // primeira execução: ainda não há dados
        }
        try {
            List<List<String>> linhas = Csv.ler(Files.readString(arquivo, StandardCharsets.UTF_8));
            if (linhas.isEmpty()) {
                return registros;              // arquivo vazio
            }
            List<String> cabecalho = linhas.get(0);          // nomes das colunas
            for (int n = 1; n < linhas.size(); n++) {
                List<String> celulas = linhas.get(n);
                Map<String, Object> mapa = new LinkedHashMap<>();
                for (int c = 0; c < cabecalho.size(); c++) {
                    String valor = c < celulas.size() ? celulas.get(c) : "";
                    if (valor.startsWith(PREFIXO_JSON)) {
                        mapa.put(cabecalho.get(c), Json.parse(valor.substring(PREFIXO_JSON.length())));
                    } else {
                        mapa.put(cabecalho.get(c), valor);
                    }
                }
                registros.add(mapa);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Não foi possível ler o arquivo " + arquivo + ": " + e.getMessage(), e);
        }
        return registros;
    }

    /** Grava tudo (primeiro em um temporário, depois troca, para não corromper o arquivo). */
    @Override
    public void gravar(List<Map<String, Object>> registros) {
        try {
            Set<String> colunas = new LinkedHashSet<>();      // junta as chaves de todos os registros
            for (Map<String, Object> m : registros) {
                colunas.addAll(m.keySet());
            }
            StringBuilder csv = new StringBuilder();
            if (!registros.isEmpty()) {
                csv.append(Csv.linha(new ArrayList<>(colunas))).append("\r\n");
                for (Map<String, Object> m : registros) {
                    List<String> celulas = new ArrayList<>();
                    for (String coluna : colunas) {
                        celulas.add(paraCelula(m.get(coluna)));
                    }
                    csv.append(Csv.linha(celulas)).append("\r\n");
                }
            }
            Path temporario = Path.of(arquivo.toString() + ".tmp");
            Files.writeString(temporario, csv.toString(), StandardCharsets.UTF_8);
            Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao gravar o arquivo " + arquivo + ": " + e.getMessage(), e);
        }
    }

    /** Converte um valor do registro no texto que vai dentro da célula do CSV. */
    private static String paraCelula(Object valor) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof Double || valor instanceof Float) {
            // Decimal com vírgula, como o Excel em português espera (a leitura aceita vírgula)
            return String.valueOf(valor).replace('.', ',');
        }
        if (valor instanceof Number || valor instanceof Boolean || valor instanceof CharSequence) {
            return String.valueOf(valor);
        }
        return PREFIXO_JSON + Json.stringify(valor);      // lista, vetor ou mapa
    }
}

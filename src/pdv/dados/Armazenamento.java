package pdv.dados;

import java.util.List;
import java.util.Map;

/**
 * Contrato de "onde os dados ficam guardados".
 *
 * O {@link Repositorio} não sabe se os registros estão em arquivos CSV ou em um
 * banco de dados: ele só chama estes dois métodos. Assim trocar o armazenamento
 * não exige mexer em nenhuma outra parte do sistema.
 *
 * Implementações: {@link ArmazenamentoCsv} (arquivos, uso local) e
 * {@link ArmazenamentoBanco} (PostgreSQL, uso na internet).
 */
public interface Armazenamento {

    /** Lê todos os registros guardados (cada registro é um Map campo -> valor). */
    List<Map<String, Object>> ler();

    /** Grava o estado completo: o que existir aqui passa a ser exatamente o que está guardado. */
    void gravar(List<Map<String, Object>> registros);
}

package pdv.dados;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import pdv.modelo.Entidade;
import pdv.util.ApiException;

/**
 * Repositório genérico: guarda uma lista de entidades em memória e a mantém
 * sincronizada com um {@link Armazenamento} (arquivo CSV ou banco de dados).
 *
 * Como é genérico ({@code <T extends Entidade>}), a MESMA classe serve para
 * produtos, clientes, fornecedores, vendas, ordens de serviço e usuários.
 *
 * Os métodos são {@code synchronized} porque o servidor atende várias
 * requisições ao mesmo tempo (várias threads).
 */
public class Repositorio<T extends Entidade> {

    private final Armazenamento armazenamento;                // onde os dados ficam guardados (CSV ou banco)
    private final Function<Map<String, Object>, T> fabrica;   // sabe criar um T a partir de um Map
    private final List<T> itens = new ArrayList<>();          // registros em memória
    private int proximoId = 1;                                // próximo identificador livre

    /**
     * @param armazenamento onde os registros são guardados
     * @param fabrica função que converte Map em objeto (ex.: {@code Produto::fromMap})
     */
    public Repositorio(Armazenamento armazenamento, Function<Map<String, Object>, T> fabrica) {
        this.armazenamento = armazenamento;
        this.fabrica = fabrica;
        carregar();
    }

    /** @return cópia da lista de registros (evita alterações acidentais na lista original) */
    public synchronized List<T> listar() {
        return new ArrayList<>(itens);
    }

    /** Procura um registro pelo id. @return o registro, ou null se não existir. */
    public synchronized T buscar(int id) {
        for (T item : itens) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    /** Procura um registro pelo id e gera erro 404 se não existir. */
    public synchronized T buscarOuErro(int id) {
        T item = buscar(id);
        if (item == null) {
            throw new ApiException(404, "Registro não encontrado.");
        }
        return item;
    }

    /**
     * Salva um registro: se o id for 0 cria um novo; senão substitui o existente.
     * Depois grava tudo no arquivo.
     */
    public synchronized T salvar(T entidade) {
        if (entidade.getId() == 0) {
            entidade.setId(proximoId++);       // novo registro: gera id
            itens.add(entidade);
        } else {
            int posicao = -1;
            for (int i = 0; i < itens.size(); i++) {
                if (itens.get(i).getId() == entidade.getId()) {
                    posicao = i;
                    break;
                }
            }
            if (posicao < 0) {
                throw new ApiException(404, "Registro não encontrado para atualização.");
            }
            itens.set(posicao, entidade);      // substitui o registro antigo
        }
        persistir();
        return entidade;
    }

    /** Remove um registro pelo id. @return true se removeu, false se não existia. */
    public synchronized boolean remover(int id) {
        boolean removeu = itens.removeIf(item -> item.getId() == id);
        if (removeu) {
            persistir();
        }
        return removeu;
    }

    /** @return true se ainda não há nenhum registro (usado para carregar os dados de exemplo). */
    public synchronized boolean vazio() {
        return itens.isEmpty();
    }

    // ---------------------------------------------------------------------
    //  Leitura e gravação (delegadas ao Armazenamento)
    // ---------------------------------------------------------------------

    /** Lê os registros guardados e transforma cada um em entidade. */
    private void carregar() {
        for (Map<String, Object> mapa : armazenamento.ler()) {
            T entidade = fabrica.apply(mapa);
            itens.add(entidade);
            proximoId = Math.max(proximoId, entidade.getId() + 1);
        }
    }

    /** Envia ao armazenamento o estado atual de todos os registros. */
    private void persistir() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (T item : itens) {
            lista.add(item.toMap());
        }
        armazenamento.gravar(lista);
    }
}

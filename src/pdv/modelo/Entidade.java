package pdv.modelo;

import java.util.Map;

/**
 * Contrato que toda "coisa cadastrável" do sistema precisa cumprir
 * (Produto, Cliente, Fornecedor, Venda, Ordem de Serviço, Usuário).
 *
 * Graças a esta interface, uma única classe genérica ({@code Repositorio<T>})
 * consegue guardar e carregar qualquer tipo de cadastro.
 */
public interface Entidade {

    /** Identificador único do registro (0 = ainda não foi salvo). */
    int getId();

    /** Define o identificador (feito pelo repositório ao salvar). */
    void setId(int id);

    /** Converte o objeto em um Map, que depois vira JSON no arquivo de dados. */
    Map<String, Object> toMap();

    /** Valida as regras do cadastro; lança IllegalArgumentException com mensagem amigável. */
    void validar();

    /** Versão enviada ao navegador. Por padrão é igual a toMap(), mas pode esconder dados sensíveis. */
    default Map<String, Object> paraApi() {
        return toMap();
    }
}

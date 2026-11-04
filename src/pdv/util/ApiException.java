package pdv.util;

/**
 * Exceção usada pelo servidor para sinalizar erros "esperados" ao cliente.
 * Carrega o código HTTP (400, 401, 403, 404...) junto com a mensagem
 * em português que será exibida na tela do usuário.
 */
public class ApiException extends RuntimeException {

    /** Identificador de versão exigido pelo Java para classes serializáveis (exceções). */
    private static final long serialVersionUID = 1L;

    /** Código de status HTTP que será devolvido ao navegador. */
    private final int status;

    /**
     * @param status   código HTTP (ex.: 400 = requisição inválida, 404 = não encontrado)
     * @param mensagem texto amigável explicando o problema
     */
    public ApiException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    /** @return código HTTP associado ao erro */
    public int getStatus() {
        return status;
    }
}

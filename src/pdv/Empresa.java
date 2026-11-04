package pdv;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dados da empresa (identidade da loja).
 *
 * >>> Para trocar o nome/dados da loja, altere SOMENTE este arquivo. <<<
 * O front end busca estas informações pela API (/api/config), então a tela de
 * apresentação, o login, o menu e o cupom das vendas se atualizam sozinhos.
 */
public final class Empresa {

    private Empresa() { }

    public static final String NOME      = "MaxRevolution";
    public static final String SUBTITULO = "Informática e Celular · Assistência Técnica";
    public static final String SLOGAN    = "Rápido. Confiável. Revolucionário.";
    public static final String DESCRICAO =
        "A MaxRevolution cuida do dia a dia tecnológico de pessoas e empresas. "
      + "Somos especialistas em informática e celular (Apple e Android): assistência técnica, "
      + "venda de aparelhos e acessórios, hardware e software em geral e atacado de peças "
      + "de reposição para lojistas e técnicos.";
    public static final String ENDERECO  = "Rua das Palmeiras, 100 - Centro";
    public static final String TELEFONE  = "(00) 0000-0000";
    public static final String EMAIL     = "contato@maxrevolution.com.br";
    public static final String CNPJ      = "00.000.000/0001-00";
    public static final String HORARIO   = "Seg a Sex: 9h às 18h | Sáb: 9h às 13h";

    /** Converte os dados da empresa em um Map para serem enviados ao navegador como JSON. */
    public static Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", NOME);
        m.put("subtitulo", SUBTITULO);
        m.put("slogan", SLOGAN);
        m.put("descricao", DESCRICAO);
        m.put("endereco", ENDERECO);
        m.put("telefone", TELEFONE);
        m.put("email", EMAIL);
        m.put("cnpj", CNPJ);
        m.put("horario", HORARIO);
        return m;
    }
}

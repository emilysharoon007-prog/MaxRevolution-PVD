package pdv.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras de segurança do sistema:
 *  1) Matriz de permissões (perfil x módulo)
 *  2) Criptografia de senhas (SHA-256 com "sal")
 */
public final class Seguranca {

    private Seguranca() { }

    // =====================================================================
    //  VETORES: perfis de acesso e módulos do sistema
    // =====================================================================

    /** Vetor com os perfis de usuário existentes. A posição no vetor = linha da matriz. */
    public static final String[] PERFIS = {"ADMIN", "VENDEDOR", "TECNICO"};

    /** Vetor com os módulos (telas) do sistema. A posição no vetor = coluna da matriz. */
    public static final String[] MODULOS = {
        "dashboard", "pdv", "produtos", "clientes", "fornecedores",
        "ordens", "vendas", "relatorios", "usuarios"
    };

    // =====================================================================
    //  MATRIZ: quem pode acessar o quê
    //  Linhas = perfis (PERFIS) | Colunas = módulos (MODULOS)
    // =====================================================================
    private static final boolean[][] PERMISSOES = {
        //  dash   pdv    prod   cli    forn   ord    vend   rel    usu
        { true,  true,  true,  true,  true,  true,  true,  true,  true  },  // ADMIN
        { true,  true,  true,  true,  false, true,  true,  false, false },  // VENDEDOR
        { true,  false, true,  true,  false, true,  false, false, false },  // TECNICO
    };

    /** Procura um texto dentro de um vetor e devolve a posição (ou -1 se não achar). */
    public static int indice(String[] vetor, String valor) {
        for (int i = 0; i < vetor.length; i++) {
            if (vetor[i].equalsIgnoreCase(valor)) {
                return i;
            }
        }
        return -1;
    }

    /** Verifica na matriz se o perfil pode acessar o módulo. */
    public static boolean permitido(String perfil, String modulo) {
        int linha = indice(PERFIS, perfil);
        int coluna = indice(MODULOS, modulo);
        return linha >= 0 && coluna >= 0 && PERMISSOES[linha][coluna];
    }

    /** Lista os módulos liberados para um perfil (usado para montar o menu do front end). */
    public static List<String> modulosDoPerfil(String perfil) {
        List<String> liberados = new ArrayList<>();
        for (String modulo : MODULOS) {
            if (permitido(perfil, modulo)) {
                liberados.add(modulo);
            }
        }
        return liberados;
    }

    /** Devolve uma cópia da matriz de permissões (para exibir na tela de apresentação). */
    public static boolean[][] matriz() {
        boolean[][] copia = new boolean[PERMISSOES.length][];
        for (int i = 0; i < PERMISSOES.length; i++) {
            copia[i] = PERMISSOES[i].clone();
        }
        return copia;
    }

    // =====================================================================
    //  SENHAS
    // =====================================================================

    /**
     * Gera o hash de uma senha no formato "sal:hash".
     * O "sal" é um valor aleatório que impede que duas senhas iguais gerem o mesmo hash.
     */
    public static String gerarHash(String senha) {
        byte[] sal = new byte[16];
        new SecureRandom().nextBytes(sal);
        String salHex = hex(sal);
        return salHex + ":" + sha256(salHex + senha);
    }

    /** Confere se a senha digitada corresponde ao hash guardado ("sal:hash"). */
    public static boolean conferir(String senha, String guardado) {
        if (guardado == null || !guardado.contains(":")) {
            return false;
        }
        String[] partes = guardado.split(":", 2);
        String calculado = sha256(partes[0] + senha);
        // MessageDigest.isEqual compara em tempo constante (evita ataques de temporização)
        return MessageDigest.isEqual(
            calculado.getBytes(StandardCharsets.UTF_8),
            partes[1].getBytes(StandardCharsets.UTF_8));
    }

    /** Calcula o SHA-256 de um texto e devolve em hexadecimal. */
    private static String sha256(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /** Converte um vetor de bytes em texto hexadecimal. */
    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

package pdv;

import java.awt.Desktop;
import java.net.BindException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import pdv.dados.Banco;
import pdv.dados.DadosIniciais;
import pdv.servidor.Servidor;

/**
 * PONTO DE ENTRADA do sistema.
 *
 * Ao executar, esta classe:
 *   1) abre a base de dados (pasta "dados") e, na primeira vez, cadastra dados de exemplo;
 *   2) liga o servidor (back end + front end) em uma porta livre;
 *   3) abre o navegador padrão já na tela de apresentação da loja.
 *
 * Uso:  java -cp out pdv.Main [porta] [--rede]
 *   porta  : (opcional) porta desejada, padrão 8080
 *   --rede : (opcional) permite acesso por outros dispositivos da mesma rede Wi-Fi
 */
public class Main {

    public static void main(String[] args) {
        // ---- Lê os argumentos opcionais da linha de comando ----
        int portaInicial = 8080;
        boolean rede = false;
        for (String arg : args) {
            if (arg.equals("--rede")) {
                rede = true;
            } else if (arg.matches("\\d{2,5}")) {
                portaInicial = Integer.parseInt(arg);
            }
        }

        // ---- Modo internet: plataformas como Render informam a porta em PORT e o banco em DATABASE_URL ----
        String portaAmbiente = System.getenv("PORT");
        if (portaAmbiente != null && portaAmbiente.matches("\\d{2,5}")) {
            portaInicial = Integer.parseInt(portaAmbiente);
            rede = true;                                  // na internet o servidor precisa aceitar conexões externas
        }
        String databaseUrl = System.getenv("DATABASE_URL");
        boolean demo = !"false".equalsIgnoreCase(System.getenv("PDV_DEMO"));   // PDV_DEMO=false desliga os dados de exemplo

        // ---- Pastas do projeto (relativas ao local onde o programa foi iniciado) ----
        Path raiz = Paths.get("").toAbsolutePath();
        Path pastaWeb = raiz.resolve("web");
        Path pastaDados = raiz.resolve("dados");
        if (!pastaWeb.resolve("index.html").toFile().exists()) {
            System.err.println("ERRO: não encontrei a pasta 'web' em " + raiz);
            System.err.println("Execute o programa a partir da pasta principal do projeto (use iniciar.bat ou iniciar.sh).");
            System.exit(1);
        }

        // ---- Base de dados ----
        Banco banco = new Banco(pastaDados, databaseUrl);
        DadosIniciais.popularSeVazio(banco, demo);
        Servidor.modoDemo = demo;
        System.out.println("    Armazenamento: " + (banco.usaBanco ? "banco de dados PostgreSQL" : "arquivos CSV (pasta dados)"));

        // ---- Servidor: tenta a porta pedida e, se estiver ocupada, as seguintes ----
        Servidor servidor = new Servidor(banco, pastaWeb);
        int porta = portaInicial;
        boolean ligou = false;
        for (int tentativa = 0; tentativa < 15 && !ligou; tentativa++) {
            try {
                servidor.iniciar(porta, rede);
                ligou = true;
            } catch (BindException ocupada) {
                porta++;                                     // porta em uso: tenta a próxima
            } catch (Exception e) {
                System.err.println("Falha ao iniciar o servidor: " + e.getMessage());
                System.exit(1);
            }
        }
        if (!ligou) {
            System.err.println("Não encontrei nenhuma porta livre. Feche outros programas e tente de novo.");
            System.exit(1);
        }

        String endereco = "http://localhost:" + porta;
        imprimirBanner(endereco, rede);
        abrirNavegador(endereco);
    }

    /** Mostra uma mensagem de boas-vindas no console, com as cores do tema (verde). */
    private static void imprimirBanner(String endereco, boolean rede) {
        String verde = "\u001B[32m";
        String reset = "\u001B[0m";
        System.out.println();
        System.out.println(verde + "  ==========================================================" + reset);
        System.out.println(verde + "    " + Empresa.NOME + " PDV  -  " + Empresa.SUBTITULO + reset);
        System.out.println(verde + "  ==========================================================" + reset);
        System.out.println("    Sistema no ar!  Acesse:  " + endereco);
        if (rede) {
            System.out.println("    (modo rede ligado: outros aparelhos podem acessar pelo IP deste computador)");
        }
        System.out.println();
        System.out.println("    Logins de demonstração:");
        System.out.println("      davi   / 1234       (Administrador)");
        System.out.println("      emilly / 1234       (Administrador)");
        System.out.println("      carla  / venda123   (Vendedor)");
        System.out.println("      rafael / tec123     (Técnico)");
        System.out.println();
        System.out.println("    Para encerrar: pressione Ctrl + C nesta janela.");
        System.out.println();
    }

    /** Tenta abrir o navegador padrão; se o sistema não permitir, o usuário abre manualmente. */
    private static void abrirNavegador(String endereco) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(endereco));
            }
        } catch (Exception e) {
            System.out.println("    (Abra o navegador e digite o endereço acima.)");
        }
    }
}

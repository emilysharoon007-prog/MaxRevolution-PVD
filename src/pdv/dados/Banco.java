package pdv.dados;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import pdv.modelo.Cliente;
import pdv.modelo.Fornecedor;
import pdv.modelo.OrdemServico;
import pdv.modelo.Produto;
import pdv.modelo.Usuario;
import pdv.modelo.Venda;

/**
 * Reúne todos os repositórios do sistema em um só lugar (nossa "base de dados").
 * Por padrão cada repositório é um arquivo .csv dentro da pasta "dados". Se a
 * variável de ambiente DATABASE_URL estiver definida (modo internet), cada
 * repositório vira uma tabela de um banco PostgreSQL.
 *
 * Os campos são públicos e finais apenas para simplificar o acesso nesta
 * aplicação pequena: {@code banco.produtos.listar()}.
 */
public class Banco {

    public final Repositorio<Produto> produtos;
    public final Repositorio<Cliente> clientes;
    public final Repositorio<Fornecedor> fornecedores;
    public final Repositorio<Venda> vendas;
    public final Repositorio<OrdemServico> ordens;
    public final Repositorio<Usuario> usuarios;

    /** Indica se os dados estão em um banco de dados (true) ou em arquivos CSV (false). */
    public final boolean usaBanco;

    /**
     * Abre (ou cria) a base de dados.
     *
     * @param pasta       diretório onde ficam os arquivos .csv (modo local)
     * @param databaseUrl endereço do PostgreSQL; se for nulo ou vazio, usa os arquivos CSV
     */
    public Banco(Path pasta, String databaseUrl) {
        usaBanco = databaseUrl != null && !databaseUrl.isBlank();
        if (!usaBanco) {
            try {
                Files.createDirectories(pasta);   // cria a pasta "dados" se ela não existir
            } catch (IOException e) {
                throw new IllegalStateException("Não foi possível criar a pasta de dados: " + pasta, e);
            }
        }
        produtos     = new Repositorio<>(armazem(pasta, databaseUrl, "produtos"),     Produto::fromMap);
        clientes     = new Repositorio<>(armazem(pasta, databaseUrl, "clientes"),     Cliente::fromMap);
        fornecedores = new Repositorio<>(armazem(pasta, databaseUrl, "fornecedores"), Fornecedor::fromMap);
        vendas       = new Repositorio<>(armazem(pasta, databaseUrl, "vendas"),       Venda::fromMap);
        ordens       = new Repositorio<>(armazem(pasta, databaseUrl, "ordens"),       OrdemServico::fromMap);
        usuarios     = new Repositorio<>(armazem(pasta, databaseUrl, "usuarios"),     Usuario::fromMap);
    }

    /** Escolhe o tipo de armazenamento de um cadastro: banco de dados ou arquivo CSV. */
    private static Armazenamento armazem(Path pasta, String databaseUrl, String nome) {
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            return new ArmazenamentoBanco(databaseUrl, nome);
        }
        return new ArmazenamentoCsv(pasta.resolve(nome + ".csv"));
    }
}

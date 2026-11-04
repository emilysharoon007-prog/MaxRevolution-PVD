# 🌿 MaxRevolution PDV

Sistema de Ponto de Venda e gestão para loja de **assistência técnica, celulares, informática e atacado de peças**.
Trabalho de *Elicitação de Requisitos e Programação em Java*.

* **Back end:** Java 17+ puro (servidor HTTP nativo `com.sun.net.httpserver`, API REST em JSON, sem frameworks).
* **Front end:** HTML + CSS + JavaScript puros, servido pelo próprio Java. Tema **preto + verde floresta/grama**.
* **Dados:** arquivos **CSV** na pasta `dados/` (abrem no Excel) no uso local; **PostgreSQL** quando publicado na internet (veja `GUIA-PUBLICAR.md`).

## ▶️ Como executar

Requisito: **JDK 17 ou superior** (`javac -version` deve funcionar).

| Sistema | Comando |
|---|---|
| Windows | dê dois cliques em `iniciar.bat` |
| Linux / macOS | `./iniciar.sh` |

O script compila, liga o servidor e abre o navegador em `http://localhost:8080`.
Opções: `iniciar.sh 9000` (outra porta) e `iniciar.sh --rede` (acessar por outros aparelhos da rede).
Para voltar aos dados de exemplo: feche o sistema e **apague a pasta `dados`**.

### Logins de demonstração
| Usuário | Senha | Perfil | Acesso |
|---|---|---|---|
| davi | 1234 | Administrador | tudo |
| emilly | 1234 | Administrador | tudo |
| carla | venda123 | Vendedor | PDV, estoque, clientes, OS, histórico |
| rafael | tec123 | Técnico | painel, estoque, clientes, OS |

## 🧩 Módulos
Painel (dashboard) · **PDV** (carrinho, varejo/atacado, desconto por volume, cupom imprimível) · Estoque (produtos) ·
Clientes · Fornecedores · **Ordens de Serviço** · Histórico de Vendas (cancelamento devolve ao estoque) ·
Relatórios · Usuários (login e perfis).

## 🔢 Onde estão os vetores e matrizes
| Onde | O quê |
|---|---|
| `Produto.CATEGORIAS`, `Venda.FORMAS_PAGAMENTO`, `OrdemServico.STATUS`, `Cliente.TIPOS` | **vetores** de opções |
| `Venda.FAIXAS_ATACADO` | **matriz** `double[][]` {unidades mínimas, % de desconto} |
| `Venda.itens` | **vetor** de objetos `ItemVenda[]` |
| `Seguranca.PERMISSOES` | **matriz** `boolean[][]` perfil × módulo |
| `Relatorios.completo()` | **matriz** `double[][]` categoria × forma de pagamento + vetores mensais |
| `Relatorios.ranking()` | *selection sort* em vetores paralelos |
| `Relatorios.estoqueBaixo()` | *bubble sort* em vetor de `Produto` |
| `DadosIniciais` | matrizes de texto/objetos com os dados de exemplo |

## 🗂️ Estrutura
```
src/pdv/Main.java            ponto de entrada
src/pdv/Empresa.java         identidade da loja (mude o nome aqui)
src/pdv/modelo/              classes de domínio (Produto, Cliente, Venda, ...)  -> interface Entidade
src/pdv/dados/               Repositorio<T> genérico (CSV), Banco, Relatorios, DadosIniciais
src/pdv/servidor/Servidor    back end: arquivos estáticos + API REST + regras
src/pdv/util/                Csv, Json (feito à mão), Seguranca (hash SHA-256 + sal)
web/                         front end (index.html, css/estilo.css, js/*.js)
dados/                       arquivos .csv (criados na 1ª execução)
```
Conceitos de POO usados: encapsulamento, interface (`Entidade`), **classe genérica** (`Repositorio<T extends Entidade>`),
classes anônimas/abstratas (`Regras<T>`), exceções próprias (`ApiException`), `synchronized` para vendas simultâneas.

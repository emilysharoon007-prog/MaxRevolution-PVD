/* ==========================================================================
   paginas.js - As páginas do sistema (uma função por módulo do menu)
   Cada função recebe o elemento onde deve se desenhar. As telas de cadastro
   usam o gerador genérico criarCadastro() (cadastros.js) e só precisam
   descrever colunas e campos.
   ========================================================================== */

const Paginas = {};

/** Transforma uma lista de textos em opções de <select>: ['A','B'] -> [{v:'A',t:'A'},...] */
const opcoesDe = (lista) => lista.map(x => ({ v: x, t: x }));

// ==========================================================================
//  PAINEL (DASHBOARD)
// ==========================================================================
Paginas.dashboard = async function (el) {
  const d = await Api.get('/dashboard');
  const c = d.cartoes;

  const rank = d.maisVendidos.length === 0
    ? '<div class="vazio">Ainda não há vendas.</div>'
    : `<ul class="lista-rank">${d.maisVendidos.map(r => `
        <li><span class="rank-pos">${r.posicao}</span>
          <span class="rank-nome">${UI.esc(r.nome)}<small class="sub">${r.quantidade} unidade(s) vendidas</small></span>
          <span class="rank-valor">${UI.moeda(r.valor)}</span></li>`).join('')}</ul>`;

  const baixo = d.estoqueBaixo.length === 0
    ? '<div class="vazio">Tudo certo: nenhum produto com estoque baixo. 🌿</div>'
    : `<ul class="lista-rank">${d.estoqueBaixo.map(p => `
        <li><span class="rank-nome">${UI.esc(p.nome)}<small class="sub">${UI.esc(p.categoria)} · mínimo ${p.minimo}</small></span>
          ${UI.badge(p.estoque + ' un.', p.estoque === 0 ? 'vermelho' : 'amarelo')}</li>`).join('')}</ul>`;

  el.innerHTML = `
    <p class="sub" style="margin-bottom:18px;font-size:.95rem">Bem-vindo(a), <b style="color:var(--grama-clara)">${UI.esc(App.usuario.nome)}</b>! Veja como está a loja hoje.</p>
    <div class="grade-stats">
      ${UI.stat('Vendas hoje', UI.moeda(c.vendasHoje), c.qtdVendasHoje + ' venda(s)')}
      ${UI.stat('Faturamento do mês', UI.moeda(c.faturamentoMes), c.qtdVendasMes + ' venda(s)')}
      ${UI.stat('Lucro do mês', UI.moeda(c.lucroMes), 'vendas − custo dos produtos')}
      ${UI.stat('Ticket médio', UI.moeda(c.ticketMedio), 'valor médio por venda')}
      ${UI.stat('Valor em estoque', UI.moeda(c.valorEstoque), c.produtosCadastrados + ' produtos cadastrados')}
      ${UI.stat('Clientes', String(c.clientes), 'cadastrados')}
      ${UI.stat('OS em aberto', String(c.osEmAberto), 'aparelhos em atendimento', c.osEmAberto > 0)}
      ${UI.stat('Estoque baixo', String(d.estoqueBaixo.length), 'produtos para repor', d.estoqueBaixo.length > 0)}
    </div>
    <div class="grade-2">
      <div class="card"><h3>Faturamento dos últimos 6 meses</h3>${UI.graficoBarras(d.faturamento6Meses.rotulos, d.faturamento6Meses.valores, UI.moedaCurta)}</div>
      <div class="card"><h3>Ordens de serviço por status</h3>${UI.graficoHorizontal(d.osPorStatus.rotulos, d.osPorStatus.valores)}</div>
    </div>
    <div class="grade-2">
      <div class="card"><h3>Produtos mais vendidos</h3>${rank}</div>
      <div class="card"><h3>Alerta de reposição</h3>${baixo}</div>
    </div>`;
};

// ==========================================================================
//  PDV (vendas) - desenhado em pdv.js
// ==========================================================================
Paginas.pdv = desenharPDV;

// ==========================================================================
//  ESTOQUE (PRODUTOS)
// ==========================================================================
Paginas.produtos = criarCadastro({
  rota: '/produtos',
  singular: 'Produto',
  contexto: async () => ({ fornecedores: await Api.get('/fornecedores') }),
  busca: (p) => `${p.codigo} ${p.nome} ${p.categoria}`,
  padrao: { categoria: Config.categorias ? Config.categorias[0] : '', estoqueMinimo: 5, fornecedorId: 0 },
  colunas: [
    { titulo: 'Código', valor: p => `<b>${UI.esc(p.codigo)}</b>` },
    { titulo: 'Produto', valor: p => `${UI.esc(p.nome)}<small class="sub">${UI.esc(p.categoria)}</small>` },
    { titulo: 'Fornecedor', valor: (p, ctx) => {
        const f = ctx.fornecedores.find(x => x.id === p.fornecedorId);
        return f ? UI.esc(f.razaoSocial) : '<span class="sub">—</span>';
      } },
    { titulo: 'Custo', classe: 'num', valor: p => UI.moeda(p.precoCusto) },
    { titulo: 'Varejo', classe: 'num', valor: p => UI.moeda(p.precoVarejo) },
    { titulo: 'Atacado', classe: 'num', valor: p => UI.moeda(p.precoAtacado) },
    { titulo: 'Estoque', classe: 'num', valor: p => UI.badge(p.estoque + ' un.', p.estoque === 0 ? 'vermelho' : p.estoqueBaixo ? 'amarelo' : 'verde') }
  ],
  campos: [
    { nome: 'codigo', label: 'Código / código de barras', obrigatorio: true },
    { nome: 'nome', label: 'Nome do produto', obrigatorio: true },
    { nome: 'categoria', label: 'Categoria', tipo: 'select', opcoes: () => opcoesDe(Config.categorias) },
    { nome: 'fornecedorId', label: 'Fornecedor', tipo: 'select', numerico: true,
      opcoes: (ctx) => [{ v: 0, t: '— sem fornecedor —' }, ...ctx.fornecedores.map(f => ({ v: f.id, t: f.razaoSocial }))] },
    { nome: 'precoCusto', label: 'Preço de custo (R$)', tipo: 'dinheiro' },
    { nome: 'precoVarejo', label: 'Preço de varejo (R$)', tipo: 'dinheiro', obrigatorio: true },
    { nome: 'precoAtacado', label: 'Preço de atacado (R$)', tipo: 'dinheiro', dica: 'Vazio/0 = usa o preço de varejo' },
    { nome: 'estoque', label: 'Quantidade em estoque', tipo: 'inteiro' },
    { nome: 'estoqueMinimo', label: 'Estoque mínimo (alerta)', tipo: 'inteiro' }
  ]
});

// ==========================================================================
//  CLIENTES
// ==========================================================================
Paginas.clientes = criarCadastro({
  rota: '/clientes',
  singular: 'Cliente',
  busca: (c) => `${c.nome} ${c.documento} ${c.telefone} ${c.email}`,
  padrao: { tipo: 'VAREJO' },
  colunas: [
    { titulo: 'Nome', valor: c => `<b>${UI.esc(c.nome)}</b><small class="sub">${UI.esc(c.documento)}</small>` },
    { titulo: 'Contato', valor: c => `${UI.esc(c.telefone)}<small class="sub">${UI.esc(c.email)}</small>` },
    { titulo: 'Endereço', valor: c => UI.esc(c.endereco) },
    { titulo: 'Tipo', valor: c => UI.badge(c.tipo === 'ATACADO' ? 'Atacado' : 'Varejo', c.tipo === 'ATACADO' ? 'azul' : 'verde') }
  ],
  campos: [
    { nome: 'nome', label: 'Nome / razão social', obrigatorio: true, largo: true },
    { nome: 'documento', label: 'CPF / CNPJ' },
    { nome: 'tipo', label: 'Tipo de cliente', tipo: 'select', opcoes: () => [{ v: 'VAREJO', t: 'Varejo' }, { v: 'ATACADO', t: 'Atacado' }] },
    { nome: 'telefone', label: 'Telefone', tipo: 'tel' },
    { nome: 'email', label: 'E-mail', tipo: 'email' },
    { nome: 'endereco', label: 'Endereço', largo: true },
    { nome: 'observacoes', label: 'Observações', tipo: 'textarea', largo: true }
  ]
});

// ==========================================================================
//  FORNECEDORES
// ==========================================================================
Paginas.fornecedores = criarCadastro({
  rota: '/fornecedores',
  singular: 'Fornecedor',
  busca: (f) => `${f.razaoSocial} ${f.documento} ${f.contato} ${f.produtosFornecidos}`,
  colunas: [
    { titulo: 'Fornecedor', valor: f => `<b>${UI.esc(f.razaoSocial)}</b><small class="sub">${UI.esc(f.documento)}</small>` },
    { titulo: 'Contato', valor: f => `${UI.esc(f.contato)}<small class="sub">${UI.esc(f.telefone)} · ${UI.esc(f.email)}</small>` },
    { titulo: 'Fornece', valor: f => UI.esc(f.produtosFornecidos) }
  ],
  campos: [
    { nome: 'razaoSocial', label: 'Razão social', obrigatorio: true, largo: true },
    { nome: 'documento', label: 'CNPJ' },
    { nome: 'contato', label: 'Pessoa de contato' },
    { nome: 'telefone', label: 'Telefone', tipo: 'tel' },
    { nome: 'email', label: 'E-mail', tipo: 'email' },
    { nome: 'produtosFornecidos', label: 'O que fornece', largo: true }
  ]
});

// ==========================================================================
//  ORDENS DE SERVIÇO (assistência técnica)
// ==========================================================================
Paginas.ordens = criarCadastro({
  rota: '/ordens',
  singular: 'Ordem de serviço',
  feminino: true,
  contexto: async () => ({ clientes: await Api.get('/clientes') }),
  busca: (o) => `${o.id} ${o.clienteNome} ${o.aparelho} ${o.defeito} ${o.status} ${o.tecnico}`,
  padrao: { status: 'Aberta', clienteId: 0 },
  colunas: [
    { titulo: 'Nº', valor: o => `<b>#${String(o.id).padStart(4, '0')}</b>` },
    { titulo: 'Cliente', valor: o => UI.esc(o.clienteNome) },
    { titulo: 'Aparelho / defeito', valor: o => `${UI.esc(o.aparelho)}<small class="sub">${UI.esc(o.defeito)}</small>` },
    { titulo: 'Técnico', valor: o => UI.esc(o.tecnico) },
    { titulo: 'Status', valor: o => UI.badgeOS(o.status) },
    { titulo: 'Total', classe: 'num', valor: o => UI.moeda(o.total) },
    { titulo: 'Entrada', valor: o => UI.data(o.dataEntrada) }
  ],
  campos: [
    { nome: 'clienteId', label: 'Cliente cadastrado', tipo: 'select', numerico: true,
      opcoes: (ctx) => [{ v: 0, t: '— cliente avulso —' }, ...ctx.clientes.map(c => ({ v: c.id, t: c.nome }))] },
    { nome: 'clienteNome', label: 'Nome (se for avulso)' },
    { nome: 'aparelho', label: 'Aparelho', obrigatorio: true, dica: 'Ex.: Smartphone 64GB, Notebook 15"' },
    { nome: 'tecnico', label: 'Técnico responsável', dica: 'Vazio = você' },
    { nome: 'defeito', label: 'Defeito relatado', tipo: 'textarea', obrigatorio: true, largo: true },
    { nome: 'status', label: 'Status', tipo: 'select', opcoes: () => opcoesDe(Config.statusOS) },
    { nome: 'valorServico', label: 'Mão de obra (R$)', tipo: 'dinheiro' },
    { nome: 'valorPecas', label: 'Peças (R$)', tipo: 'dinheiro' },
    { nome: 'observacoes', label: 'Observações', tipo: 'textarea', largo: true }
  ]
});

// ==========================================================================
//  HISTÓRICO DE VENDAS
// ==========================================================================
Paginas.vendas = async function (el) {
  let vendas = await Api.get('/vendas');
  let termo = '';
  const ehAdmin = App.usuario.perfil === 'ADMIN';

  el.innerHTML = `
    <div class="barra-ferramentas">
      <div class="busca"><input type="search" id="v-busca" placeholder="Buscar por cliente, vendedor, pagamento ou nº..."></div>
    </div>
    <div class="tabela-wrap"><table class="tabela">
      <thead><tr><th>Nº</th><th>Data</th><th>Cliente</th><th>Vendedor</th><th>Tipo</th><th>Pagamento</th><th class="num">Total</th><th>Status</th><th></th></tr></thead>
      <tbody id="v-corpo"></tbody>
    </table></div>
    <p class="sub" style="margin-top:10px" id="v-contagem"></p>`;

  function desenhar() {
    const lista = vendas.filter(v =>
      `${v.id} ${v.clienteNome} ${v.vendedor} ${v.formaPagamento} ${v.tipo}`.toLowerCase().includes(termo));
    const somaValida = lista.filter(v => v.status === 'CONCLUIDA').reduce((s, v) => s + v.total, 0);
    el.querySelector('#v-corpo').innerHTML = lista.length === 0
      ? '<tr><td class="vazio" colspan="9">Nenhuma venda encontrada.</td></tr>'
      : lista.map(v => `
        <tr>
          <td><b>#${String(v.id).padStart(4, '0')}</b></td>
          <td>${UI.dataHora(v.data)}</td>
          <td>${UI.esc(v.clienteNome)}</td>
          <td>${UI.esc(v.vendedor)}</td>
          <td>${UI.badge(v.tipo === 'ATACADO' ? 'Atacado' : 'Varejo', v.tipo === 'ATACADO' ? 'azul' : 'verde')}</td>
          <td>${UI.esc(v.formaPagamento)}</td>
          <td class="num"><b>${UI.moeda(v.total)}</b></td>
          <td>${UI.badge(v.status === 'CONCLUIDA' ? 'Concluída' : 'Cancelada', v.status === 'CONCLUIDA' ? 'verde' : 'vermelho')}</td>
          <td class="acoes">
            <button class="btn pequeno" data-op="cupom" data-id="${v.id}">🧾 Cupom</button>
            ${ehAdmin && v.status === 'CONCLUIDA' ? `<button class="btn pequeno perigo" data-op="cancelar" data-id="${v.id}">Cancelar</button>` : ''}
          </td>
        </tr>`).join('');
    el.querySelector('#v-contagem').textContent =
      `${lista.length} venda(s) · soma das concluídas: ${UI.moeda(somaValida)}`;
  }

  el.querySelector('#v-busca').addEventListener('input', (e) => { termo = e.target.value.toLowerCase(); desenhar(); });
  el.querySelector('#v-corpo').addEventListener('click', async (e) => {
    const b = e.target.closest('button[data-op]');
    if (!b) return;
    const venda = vendas.find(v => v.id === Number(b.dataset.id));
    if (b.dataset.op === 'cupom') {
      Cupom.abrir(venda);
    } else if (await UI.confirmar(`Cancelar a venda nº ${venda.id}? Os itens voltarão ao estoque.`, 'Cancelar venda')) {
      try {
        await Api.post('/vendas/' + venda.id + '/cancelar');
        UI.toast('Venda cancelada e estoque devolvido.');
        vendas = await Api.get('/vendas');
        desenhar();
      } catch (erro) {
        UI.toast(erro.message, 'erro');
      }
    }
  });
  desenhar();
};

// ==========================================================================
//  RELATÓRIOS
// ==========================================================================
Paginas.relatorios = async function (el) {
  const r = await Api.get('/relatorios');
  const matriz = r.matrizCategoriaPagamento;          // MATRIZ: linhas = categorias, colunas = formas de pagamento

  // ---- Totais por linha e por coluna (percorrendo a matriz com dois laços) ----
  const totalLinha = matriz.map(() => 0);
  const totalColuna = r.pagamentos.map(() => 0);
  let totalGeral = 0, maior = 0;
  for (let i = 0; i < matriz.length; i++) {
    for (let j = 0; j < matriz[i].length; j++) {
      totalLinha[i] += matriz[i][j];
      totalColuna[j] += matriz[i][j];
      totalGeral += matriz[i][j];
      maior = Math.max(maior, matriz[i][j]);
    }
  }

  /** Célula da matriz com cor mais forte quanto maior o valor ("mapa de calor"). */
  const celula = (valor) => {
    const intensidade = maior > 0 ? valor / maior : 0;
    return `<td class="calor" style="background:rgba(92,211,92,${(intensidade * 0.5).toFixed(2)})">${valor > 0 ? UI.moeda(valor) : '—'}</td>`;
  };

  const linhasMatriz = matriz.map((linha, i) => `
    <tr><td><b>${UI.esc(r.categorias[i])}</b></td>${linha.map(celula).join('')}<td class="total num">${UI.moeda(totalLinha[i])}</td></tr>`).join('');

  const ranking = r.ranking.length === 0 ? '<div class="vazio">Sem vendas.</div>' :
    `<ul class="lista-rank">${r.ranking.map(x => `
      <li><span class="rank-pos">${x.posicao}</span>
        <span class="rank-nome">${UI.esc(x.nome)}<small class="sub">${x.quantidade} unidade(s)</small></span>
        <span class="rank-valor">${UI.moeda(x.valor)}</span></li>`).join('')}</ul>`;

  el.innerHTML = `
    <div class="grade-stats">
      ${UI.stat('Total vendido', UI.moeda(r.resumo.totalVendido), r.resumo.qtdVendas + ' vendas concluídas')}
      ${UI.stat('Lucro total', UI.moeda(r.resumo.totalLucro), 'após custo dos produtos')}
      ${UI.stat('Ticket médio', UI.moeda(r.resumo.ticketMedio), 'por venda')}
      ${UI.stat('Margem', (r.resumo.totalVendido > 0 ? (r.resumo.totalLucro / r.resumo.totalVendido * 100) : 0).toFixed(1).replace('.', ',') + '%', 'lucro ÷ vendas')}
    </div>
    <div class="grade-2">
      <div class="card"><h3>Faturamento mensal (ano atual)</h3>${UI.graficoBarras(r.mensal.rotulos, r.mensal.valores, UI.moedaCurta)}</div>
      <div class="card"><h3>Varejo × Atacado</h3>${UI.graficoHorizontal(r.porTipo.rotulos, r.porTipo.valores, UI.moeda)}
        <h3 style="margin-top:26px">Lucro por categoria</h3>${UI.graficoHorizontal(r.lucroPorCategoria.rotulos, r.lucroPorCategoria.valores, UI.moeda)}</div>
    </div>
    <div class="card" style="margin-bottom:20px">
      <h3>Matriz: categoria × forma de pagamento</h3>
      <div class="tabela-wrap"><table class="tabela matriz">
        <thead><tr><th>Categoria</th>${r.pagamentos.map(p => `<th class="num">${UI.esc(p)}</th>`).join('')}<th class="num">Total</th></tr></thead>
        <tbody>${linhasMatriz}</tbody>
        <tfoot><tr><td>Total</td>${totalColuna.map(t => `<td class="num">${UI.moeda(t)}</td>`).join('')}<td class="num">${UI.moeda(totalGeral)}</td></tr></tfoot>
      </table></div>
    </div>
    <div class="card"><h3>Top 10 produtos mais vendidos</h3>${ranking}</div>`;
};

// ==========================================================================
//  USUÁRIOS (somente administrador)
// ==========================================================================
Paginas.usuarios = criarCadastro({
  rota: '/usuarios',
  singular: 'Usuário',
  busca: (u) => `${u.nome} ${u.login} ${u.perfil}`,
  padrao: { perfil: 'VENDEDOR', ativo: true },
  colunas: [
    { titulo: 'Nome', valor: u => `<b>${UI.esc(u.nome)}</b>` },
    { titulo: 'Login', valor: u => UI.esc(u.login) },
    { titulo: 'Perfil', valor: u => UI.badge(u.perfil, u.perfil === 'ADMIN' ? 'verde' : u.perfil === 'VENDEDOR' ? 'azul' : 'amarelo') },
    { titulo: 'Situação', valor: u => UI.badge(u.ativo ? 'Ativo' : 'Inativo', u.ativo ? 'verde' : 'cinza') }
  ],
  campos: [
    { nome: 'nome', label: 'Nome completo', obrigatorio: true },
    { nome: 'login', label: 'Login', obrigatorio: true },
    { nome: 'senha', label: 'Senha (mín. 4 caracteres)', tipo: 'senha' },
    { nome: 'perfil', label: 'Perfil de acesso', tipo: 'select', opcoes: () => opcoesDe(Config.perfis) },
    { nome: 'ativo', label: 'Situação', tipo: 'checkbox', rotuloCheck: 'Usuário ativo' }
  ]
});

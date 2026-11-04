/* ==========================================================================
   pdv.js - Tela de vendas (PDV) e cupom
   Fluxo: o operador escolhe produtos -> o carrinho é enviado ao servidor para
   SIMULAR os totais (preço de atacado, desconto por volume etc.) -> ao finalizar,
   o servidor grava a venda e baixa o estoque.
   ========================================================================== */

/** Gera e exibe o cupom (recibo) de uma venda. */
const Cupom = {

  /** Monta o HTML do cupom a partir de uma venda devolvida pela API. */
  html(v) {
    const e = Config.empresa;
    const linhas = v.itens.map(i => `
      <tr><td colspan="2">${UI.esc(i.nome)}</td></tr>
      <tr><td>${i.quantidade} x ${UI.moeda(i.precoUnitario)}</td><td class="d">${UI.moeda(i.subtotal)}</td></tr>`).join('');
    const descAtacado = v.descontoAtacado > 0
      ? `<tr><td>Desc. atacado (${v.percentualAtacado}%)</td><td class="d">- ${UI.moeda(v.descontoAtacado)}</td></tr>` : '';
    const descManual = v.desconto > 0
      ? `<tr><td>Desconto</td><td class="d">- ${UI.moeda(v.desconto)}</td></tr>` : '';
    return `
      <div class="cupom">
        <h4>${UI.esc(e.nome.toUpperCase())}</h4>
        <div class="centro">${UI.esc(e.subtitulo)}</div>
        <div class="centro">${UI.esc(e.endereco)}</div>
        <div class="centro">Tel. ${UI.esc(e.telefone)} · CNPJ ${UI.esc(e.cnpj)}</div>
        <hr>
        <div>CUPOM Nº ${String(v.id).padStart(6, '0')} · ${UI.dataHora(v.data)}</div>
        <div>Cliente: ${UI.esc(v.clienteNome)}</div>
        <div>Vendedor: ${UI.esc(v.vendedor)} · ${UI.esc(v.tipo)}</div>
        <hr>
        <table>${linhas}</table>
        <hr>
        <table>
          <tr><td>Subtotal</td><td class="d">${UI.moeda(v.subtotal)}</td></tr>
          ${descAtacado}${descManual}
          <tr class="total"><td class="total">TOTAL</td><td class="d total">${UI.moeda(v.total)}</td></tr>
          <tr><td>Pagamento</td><td class="d">${UI.esc(v.formaPagamento)}</td></tr>
        </table>
        ${v.status === 'CANCELADA' ? '<hr><div class="centro"><b>*** VENDA CANCELADA ***</b></div>' : ''}
        <hr>
        <div class="centro">Obrigado pela preferência!</div>
        <div class="centro">${UI.esc(e.horario)}</div>
      </div>`;
  },

  /** Abre o cupom em uma janela com o botão de imprimir. */
  abrir(venda) {
    UI.modal({
      titulo: 'Venda nº ' + venda.id,
      corpo: Cupom.html(venda),
      botoes: [
        { texto: 'Fechar' },
        { texto: '🖨️ Imprimir', classe: 'primario', aoClicar: () => { window.print(); return false; } }
      ]
    });
  }
};

/** Desenha a tela do PDV dentro do elemento recebido. */
async function desenharPDV(el) {

  // ---- Dados vindos do servidor ----
  let produtos = await Api.get('/produtos');
  const clientes = await Api.get('/clientes');

  // ---- Estado da venda em andamento ----
  const carrinho = new Map();      // produtoId -> quantidade
  let tipo = 'VAREJO';             // VAREJO ou ATACADO
  let categoria = 'Todas';         // filtro de categoria
  let termo = '';                  // texto da busca
  let simulada = null;             // última resposta de /vendas/simular
  let mensagemErro = '';           // erro de estoque/regra vindo do servidor

  const produtoPorId = (id) => produtos.find(p => p.id === id);

  // ---- Estrutura da tela ----
  el.innerHTML = `
    <div class="pdv">
      <div class="pdv-esq">
        <div class="barra-ferramentas" style="margin-bottom:0">
          <div class="busca"><input type="search" id="pdv-busca" placeholder="Buscar por nome ou código (Enter adiciona)..." autofocus></div>
        </div>
        <div class="chips" id="pdv-chips"></div>
        <div class="grade-produtos" id="pdv-produtos"></div>
      </div>
      <div class="pdv-dir card">
        <h3>Carrinho</h3>
        <div class="seg" id="pdv-tipo">
          <button data-tipo="VAREJO" class="ativo">Varejo</button>
          <button data-tipo="ATACADO">Atacado</button>
        </div>
        <div class="campo"><label for="pdv-cliente">Cliente</label>
          <select id="pdv-cliente">
            <option value="0">Consumidor final</option>
            ${clientes.map(c => `<option value="${c.id}" data-tipo="${UI.esc(c.tipo)}">${UI.esc(c.nome)} (${UI.esc(c.tipo.toLowerCase())})</option>`).join('')}
          </select>
        </div>
        <div class="cart-itens" id="pdv-itens"></div>
        <div id="pdv-aviso"></div>
        <div class="duas-col">
          <div class="campo"><label for="pdv-pagamento">Pagamento</label>
            <select id="pdv-pagamento">${Config.formasPagamento.map(f => `<option>${UI.esc(f)}</option>`).join('')}</select>
          </div>
          <div class="campo"><label for="pdv-desconto">Desconto (R$)</label>
            <input type="number" id="pdv-desconto" min="0" step="0.01" value="0">
          </div>
        </div>
        <div class="cart-totais" id="pdv-totais"></div>
        <button class="btn primario grande bloco" id="pdv-finalizar" disabled>✔ Finalizar venda</button>
        <button class="btn fantasma bloco" id="pdv-limpar">Limpar carrinho</button>
      </div>
    </div>`;

  // ---------------------------------------------------------------------
  //  VITRINE DE PRODUTOS
  // ---------------------------------------------------------------------

  /** Preço que será cobrado do produto no tipo de venda atual. */
  const precoDe = (p) => (tipo === 'ATACADO' && p.precoAtacado > 0) ? p.precoAtacado : p.precoVarejo;

  /** Desenha os botões de categoria. */
  function desenharChips() {
    const nomes = ['Todas', ...Config.categorias];
    el.querySelector('#pdv-chips').innerHTML = nomes.map(n =>
      `<span class="chip ${n === categoria ? 'ativo' : ''}" data-cat="${UI.esc(n)}">${UI.esc(n)}</span>`).join('');
  }

  /** Lista de produtos que passam pelo filtro de categoria e de busca. */
  function filtrados() {
    return produtos.filter(p =>
      (categoria === 'Todas' || p.categoria === categoria) &&
      (p.nome + ' ' + p.codigo).toLowerCase().includes(termo));
  }

  /** Desenha os cartões de produto. */
  function desenharProdutos() {
    const lista = filtrados();
    el.querySelector('#pdv-produtos').innerHTML = lista.length === 0
      ? '<div class="vazio" style="grid-column:1/-1">Nenhum produto encontrado.</div>'
      : lista.map(p => `
        <button class="prod-card ${p.estoque <= 0 ? 'esgotado' : ''}" data-id="${p.id}" ${p.estoque <= 0 ? 'disabled' : ''}>
          <span class="cod">${UI.esc(p.codigo)}</span>
          <span class="nome">${UI.esc(p.nome)}</span>
          <span class="preco">${UI.moeda(precoDe(p))}</span>
          <span>${p.estoque <= 0 ? UI.badge('Esgotado', 'vermelho') : UI.badge(p.estoque + ' em estoque', p.estoqueBaixo ? 'amarelo' : 'verde')}</span>
        </button>`).join('');
  }

  // ---------------------------------------------------------------------
  //  CARRINHO
  // ---------------------------------------------------------------------

  /** Soma 1 unidade (respeitando o estoque). */
  function adicionar(id) {
    const p = produtoPorId(id);
    const atual = carrinho.get(id) || 0;
    if (!p || atual + 1 > p.estoque) {
      UI.toast('Estoque insuficiente para "' + (p ? p.nome : 'produto') + '".', 'erro');
      return;
    }
    carrinho.set(id, atual + 1);
    atualizar();
  }

  /** Altera a quantidade de uma linha (0 remove). */
  function definirQuantidade(id, qtd) {
    const p = produtoPorId(id);
    if (qtd <= 0) {
      carrinho.delete(id);
    } else if (p && qtd > p.estoque) {
      UI.toast('Só há ' + p.estoque + ' unidade(s) de "' + p.nome + '".', 'erro');
      carrinho.set(id, p.estoque);
    } else {
      carrinho.set(id, qtd);
    }
    atualizar();
  }

  /** Pede ao servidor os totais do carrinho atual (sem gravar nada). */
  async function simular() {
    mensagemErro = '';
    simulada = null;
    if (carrinho.size === 0) return;
    try {
      simulada = await Api.post('/vendas/simular', corpoVenda());
    } catch (e) {
      mensagemErro = e.message;
    }
  }

  /** Dados da venda no formato que a API espera. */
  function corpoVenda() {
    return {
      tipo,
      clienteId: Number(el.querySelector('#pdv-cliente').value),
      formaPagamento: el.querySelector('#pdv-pagamento').value,
      desconto: Number(el.querySelector('#pdv-desconto').value || 0),
      itens: [...carrinho.entries()].map(([produtoId, quantidade]) => ({ produtoId, quantidade }))
    };
  }

  /** Dica sobre o desconto por volume, lendo a MATRIZ de faixas enviada pelo servidor. */
  function dicaAtacado(unidades) {
    if (tipo !== 'ATACADO') return '';
    const faixas = Config.faixasAtacado;                 // [[unidades mínimas, % desconto], ...]
    const proxima = faixas.find(f => unidades < f[0]);   // primeira faixa ainda não atingida
    if (!proxima) return `<div class="dica-atacado">🏆 Desconto máximo de atacado aplicado!</div>`;
    return `<div class="dica-atacado">📦 Faltam ${proxima[0] - unidades} un. para ${proxima[1]}% de desconto de atacado.</div>`;
  }

  /** Redesenha carrinho e totais (chama o servidor para calcular). */
  async function atualizar() {
    await simular();
    desenharItens();
    desenharTotais();
  }

  /** Lista de itens do carrinho. */
  function desenharItens() {
    const alvo = el.querySelector('#pdv-itens');
    if (carrinho.size === 0) {
      alvo.innerHTML = '<div class="vazio" style="padding:26px 6px">Carrinho vazio.<br><small>Clique em um produto para adicionar.</small></div>';
      return;
    }
    alvo.innerHTML = [...carrinho.entries()].map(([id, qtd]) => {
      const p = produtoPorId(id);
      return `
        <div class="cart-item">
          <div>
            <div class="nome">${UI.esc(p.nome)}</div>
            <div class="linha-preco">${UI.moeda(precoDe(p))} cada</div>
          </div>
          <div class="total-item">${UI.moeda(precoDe(p) * qtd)}</div>
          <div class="qtd-ctrl">
            <button data-op="menos" data-id="${id}">−</button>
            <b>${qtd}</b>
            <button data-op="mais" data-id="${id}">+</button>
          </div>
          <div style="text-align:right"><button class="btn pequeno perigo" data-op="remover" data-id="${id}">🗑️</button></div>
        </div>`;
    }).join('');
  }

  /** Quadro de totais e estado do botão Finalizar. */
  function desenharTotais() {
    const unidades = [...carrinho.values()].reduce((a, b) => a + b, 0);
    el.querySelector('#pdv-aviso').innerHTML =
      (mensagemErro ? `<div class="aviso-erro">${UI.esc(mensagemErro)}</div>` : '') + dicaAtacado(unidades);

    const v = simulada;
    el.querySelector('#pdv-totais').innerHTML = `
      <div class="linha"><span>Itens (${unidades})</span><span>${UI.moeda(v ? v.subtotal : 0)}</span></div>
      ${v && v.descontoAtacado > 0 ? `<div class="linha desc"><span>Desconto atacado (${v.percentualAtacado}%)</span><span>− ${UI.moeda(v.descontoAtacado)}</span></div>` : ''}
      ${v && v.desconto > 0 ? `<div class="linha desc"><span>Desconto</span><span>− ${UI.moeda(v.desconto)}</span></div>` : ''}
      <div class="linha total"><span>TOTAL</span><span>${UI.moeda(v ? v.total : 0)}</span></div>`;
    el.querySelector('#pdv-finalizar').disabled = !v;
  }

  /** Grava a venda de verdade. */
  async function finalizar() {
    const botao = el.querySelector('#pdv-finalizar');
    botao.disabled = true;
    try {
      const venda = await Api.post('/vendas', corpoVenda());
      UI.toast('Venda nº ' + venda.id + ' registrada! Total ' + UI.moeda(venda.total));
      carrinho.clear();
      el.querySelector('#pdv-desconto').value = 0;
      produtos = await Api.get('/produtos');        // o estoque mudou: recarrega
      desenharProdutos();
      await atualizar();
      Cupom.abrir(venda);
    } catch (e) {
      UI.toast(e.message, 'erro');
      botao.disabled = false;
    }
  }

  // ---------------------------------------------------------------------
  //  EVENTOS
  // ---------------------------------------------------------------------
  const busca = el.querySelector('#pdv-busca');
  busca.addEventListener('input', () => { termo = busca.value.trim().toLowerCase(); desenharProdutos(); });
  busca.addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    // Enter adiciona: o produto de código exato, ou o único resultado da busca
    const exato = produtos.find(p => p.codigo.toLowerCase() === termo);
    const lista = filtrados();
    const alvo = exato || (lista.length === 1 ? lista[0] : null);
    if (alvo && alvo.estoque > 0) {
      adicionar(alvo.id);
      busca.value = ''; termo = ''; desenharProdutos();
    } else if (!alvo) {
      UI.toast('Refine a busca: ' + lista.length + ' produtos encontrados.', 'erro');
    }
  });

  el.querySelector('#pdv-chips').addEventListener('click', (e) => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    categoria = chip.dataset.cat;
    desenharChips();
    desenharProdutos();
  });

  el.querySelector('#pdv-produtos').addEventListener('click', (e) => {
    const card = e.target.closest('.prod-card');
    if (card && !card.disabled) adicionar(Number(card.dataset.id));
  });

  el.querySelector('#pdv-itens').addEventListener('click', (e) => {
    const b = e.target.closest('button[data-op]');
    if (!b) return;
    const id = Number(b.dataset.id);
    const qtd = carrinho.get(id) || 0;
    if (b.dataset.op === 'mais') adicionar(id);
    else if (b.dataset.op === 'menos') definirQuantidade(id, qtd - 1);
    else definirQuantidade(id, 0);
  });

  el.querySelector('#pdv-tipo').addEventListener('click', (e) => {
    const b = e.target.closest('button[data-tipo]');
    if (!b) return;
    tipo = b.dataset.tipo;
    el.querySelectorAll('#pdv-tipo button').forEach(x => x.classList.toggle('ativo', x === b));
    desenharProdutos();     // os preços mudam entre varejo e atacado
    atualizar();
  });

  // Escolher um cliente de atacado muda a venda para ATACADO automaticamente
  el.querySelector('#pdv-cliente').addEventListener('change', (e) => {
    const opcao = e.target.selectedOptions[0];
    if (opcao.dataset.tipo === 'ATACADO' && tipo !== 'ATACADO') {
      el.querySelector('#pdv-tipo button[data-tipo="ATACADO"]').click();
    } else {
      atualizar();
    }
  });
  el.querySelector('#pdv-pagamento').addEventListener('change', atualizar);
  el.querySelector('#pdv-desconto').addEventListener('change', atualizar);
  el.querySelector('#pdv-finalizar').addEventListener('click', finalizar);
  el.querySelector('#pdv-limpar').addEventListener('click', () => { carrinho.clear(); atualizar(); });

  // ---- Primeira exibição ----
  desenharChips();
  desenharProdutos();
  await atualizar();
}

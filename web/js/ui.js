/* ==========================================================================
   ui.js - Funções de interface reutilizadas por todas as telas:
   formatação, avisos (toast), janelas modais, gráficos e etiquetas.
   ========================================================================== */

const UI = {

  /** Protege contra HTML malicioso: troca <, >, &, aspas por códigos seguros. Use em todo texto vindo do usuário. */
  esc(texto) {
    return String(texto ?? '').replace(/[&<>"']/g, c => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
  },

  /** Formata número como moeda brasileira: 1234.5 -> R$ 1.234,50 */
  moeda(valor) {
    return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  },

  /** Moeda compacta para os gráficos: 12500 -> R$ 12,5 mil */
  moedaCurta(valor) {
    const v = Number(valor || 0);
    if (v >= 1000) return 'R$ ' + (v / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mil';
    return 'R$ ' + v.toLocaleString('pt-BR', { maximumFractionDigits: 0 });
  },

  /** Formata data ISO como dd/mm/aaaa hh:mm */
  dataHora(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString('pt-BR') + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  },

  /** Formata data ISO como dd/mm/aaaa */
  data(iso) {
    return iso ? new Date(iso).toLocaleDateString('pt-BR') : '—';
  },

  // ----------------------------------------------------------------------
  //  AVISOS (toast)
  // ----------------------------------------------------------------------

  /** Mostra uma mensagem rápida no canto da tela. tipo: 'ok' ou 'erro'. */
  toast(mensagem, tipo = 'ok') {
    const el = document.createElement('div');
    el.className = 'toast' + (tipo === 'erro' ? ' erro' : '');
    el.textContent = (tipo === 'erro' ? '⚠️ ' : '✅ ') + mensagem;
    document.getElementById('toasts').appendChild(el);
    setTimeout(() => el.remove(), 4200);
  },

  // ----------------------------------------------------------------------
  //  JANELA MODAL
  // ----------------------------------------------------------------------

  /**
   * Abre uma janela modal.
   * @param {object} opcoes { titulo, corpo (HTML), botoes:[{texto, classe, aoClicar}], estreito }
   *   - aoClicar pode ser async; se devolver false, a janela continua aberta.
   * @returns {{el: HTMLElement, fechar: Function}}
   */
  modal({ titulo, corpo, botoes = [], estreito = false }) {
    const overlay = document.createElement('div');
    overlay.className = 'overlay';
    overlay.innerHTML = `
      <div class="modal ${estreito ? 'estreito' : ''}" role="dialog" aria-modal="true">
        <div class="modal-cab"><h2>${UI.esc(titulo)}</h2><button class="modal-x" aria-label="Fechar">✕</button></div>
        <div class="modal-corpo">${corpo}</div>
        <div class="modal-rodape"></div>
      </div>`;
    document.getElementById('modal-raiz').appendChild(overlay);

    const fechar = () => { overlay.remove(); document.removeEventListener('keydown', aoTeclar); };
    const aoTeclar = (e) => { if (e.key === 'Escape') fechar(); };
    document.addEventListener('keydown', aoTeclar);
    overlay.querySelector('.modal-x').onclick = fechar;
    overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) fechar(); });

    // Cria os botões do rodapé
    const rodape = overlay.querySelector('.modal-rodape');
    if (botoes.length === 0) rodape.remove();
    botoes.forEach(b => {
      const botao = document.createElement('button');
      botao.className = 'btn ' + (b.classe || '');
      botao.textContent = b.texto;
      botao.onclick = async () => {
        botao.disabled = true;
        try {
          const resultado = b.aoClicar ? await b.aoClicar({ el: overlay, fechar }) : undefined;
          if (resultado !== false) fechar();
        } finally {
          botao.disabled = false;
        }
      };
      rodape.appendChild(botao);
    });
    return { el: overlay, fechar };
  },

  /** Pede confirmação ao usuário. Devolve uma Promise: true (confirmou) ou false. */
  confirmar(mensagem, textoBotao = 'Confirmar', perigo = true) {
    return new Promise(resolver => {
      let respondido = false;
      const responder = (valor) => { respondido = true; resolver(valor); };
      const janela = UI.modal({
        titulo: 'Confirmação',
        corpo: `<p>${UI.esc(mensagem)}</p>`,
        estreito: true,
        botoes: [
          { texto: 'Cancelar', aoClicar: () => responder(false) },
          { texto: textoBotao, classe: perigo ? 'perigo' : 'primario', aoClicar: () => responder(true) }
        ]
      });
      // Se fechar no X ou clicando fora, conta como "não"
      const observador = new MutationObserver(() => {
        if (!document.body.contains(janela.el)) { observador.disconnect(); if (!respondido) resolver(false); }
      });
      observador.observe(document.getElementById('modal-raiz'), { childList: true });
    });
  },

  // ----------------------------------------------------------------------
  //  GRÁFICOS (feitos só com HTML e CSS)
  // ----------------------------------------------------------------------

  /** Gráfico de barras verticais. Recebe vetores de rótulos e valores. */
  graficoBarras(rotulos, valores, formatar = (v) => v) {
    const maior = Math.max(...valores, 1);
    return `<div class="grafico">${rotulos.map((r, i) => {
      const altura = Math.max((valores[i] / maior) * 78, valores[i] > 0 ? 3 : 1);
      return `<div class="barra-col" title="${UI.esc(r)}: ${UI.esc(formatar(valores[i]))}">
        <div class="barra-valor">${valores[i] > 0 ? UI.esc(formatar(valores[i])) : ''}</div>
        <div class="barra" style="height:${altura}%"></div>
        <div class="barra-rotulo">${UI.esc(r)}</div>
      </div>`;
    }).join('')}</div>`;
  },

  /** Gráfico de barras horizontais (bom para listas de categorias/status). */
  graficoHorizontal(rotulos, valores, formatar = (v) => v) {
    const maior = Math.max(...valores, 1);
    return rotulos.map((r, i) => `
      <div class="hbarra-linha">
        <span>${UI.esc(r)}</span>
        <div class="hbarra-trilho"><div class="hbarra-preenchido" style="width:${(valores[i] / maior) * 100}%"></div></div>
        <span>${UI.esc(formatar(valores[i]))}</span>
      </div>`).join('');
  },

  // ----------------------------------------------------------------------
  //  ETIQUETAS (badges)
  // ----------------------------------------------------------------------

  /** Cria uma etiqueta colorida. cor: verde, amarelo, vermelho, azul ou cinza. */
  badge(texto, cor = 'cinza') {
    return `<span class="badge ${cor}">${UI.esc(texto)}</span>`;
  },

  /** Cor da etiqueta para cada status de ordem de serviço. */
  badgeOS(status) {
    const cores = {
      'Aberta': 'azul', 'Em análise': 'amarelo', 'Aguardando peça': 'amarelo',
      'Em reparo': 'azul', 'Pronta': 'verde', 'Entregue': 'verde', 'Cancelada': 'vermelho'
    };
    return UI.badge(status, cores[status] || 'cinza');
  },

  /** Cartão de estatística usado no dashboard. */
  stat(rotulo, valor, sub = '', alerta = false) {
    return `<div class="stat ${alerta ? 'alerta' : ''}">
      <div class="stat-rotulo">${UI.esc(rotulo)}</div>
      <div class="stat-valor">${UI.esc(valor)}</div>
      <div class="stat-sub">${UI.esc(sub)}</div>
    </div>`;
  }
};

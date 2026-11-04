/* ==========================================================================
   api.js - Comunicação com o BACK END (servidor Java)
   Todas as chamadas ao servidor passam por aqui. O token de login é guardado
   na sessão do navegador e enviado automaticamente em cada requisição.
   ========================================================================== */

/** Dados vindos de /api/config (empresa, categorias, matrizes...). Preenchido no início. */
const Config = {};

const Api = {
  /** Token da sessão atual (vazio quando ninguém está logado). */
  token: sessionStorage.getItem('token') || '',

  /** Guarda (ou apaga) o token de login. */
  definirToken(valor) {
    this.token = valor || '';
    if (valor) sessionStorage.setItem('token', valor);
    else sessionStorage.removeItem('token');
  },

  /**
   * Faz uma requisição para a API.
   * @param {string} metodo GET, POST, PUT ou DELETE
   * @param {string} url    caminho depois de /api (ex.: '/produtos')
   * @param {object} corpo  dados a enviar (opcional)
   * @returns {Promise<any>} resposta já convertida de JSON
   */
  async chamar(metodo, url, corpo) {
    const opcoes = { method: metodo, headers: {} };
    if (this.token) opcoes.headers['Authorization'] = 'Bearer ' + this.token;
    if (corpo !== undefined) {
      opcoes.headers['Content-Type'] = 'application/json';
      opcoes.body = JSON.stringify(corpo);
    }

    let resposta;
    try {
      resposta = await fetch('/api' + url, opcoes);
    } catch (e) {
      throw new Error('Não foi possível falar com o servidor. Ele está ligado?');
    }

    const dados = await resposta.json().catch(() => ({}));

    // Sessão vencida: volta para a tela de apresentação
    if (resposta.status === 401 && url !== '/login') {
      this.definirToken('');
      if (typeof App !== 'undefined') App.mostrarApresentacao();
    }
    if (!resposta.ok) throw new Error(dados.erro || 'Erro inesperado (' + resposta.status + ')');
    return dados;
  },

  get(url)         { return this.chamar('GET', url); },
  post(url, corpo) { return this.chamar('POST', url, corpo === undefined ? {} : corpo); },
  put(url, corpo)  { return this.chamar('PUT', url, corpo); },
  del(url)         { return this.chamar('DELETE', url); }
};

/* ==========================================================================
   app.js - Controle geral do sistema (carregado por último)
   Cuida de: tela de apresentação, login, menu lateral e troca de páginas.
   ========================================================================== */

const App = {

  /** Usuário logado: { id, nome, login, perfil }. */
  usuario: null,

  /** Módulos liberados para o perfil do usuário (vem do servidor). */
  modulos: [],

  /** Descrição do menu: ícone e título de cada módulo. A ORDEM aqui define a ordem do menu. */
  MENU: [
    { id: 'dashboard',    icone: '📊', titulo: 'Painel' },
    { id: 'pdv',          icone: '🛒', titulo: 'PDV - Vendas' },
    { id: 'produtos',     icone: '📦', titulo: 'Estoque' },
    { id: 'clientes',     icone: '👥', titulo: 'Clientes' },
    { id: 'fornecedores', icone: '🚚', titulo: 'Fornecedores' },
    { id: 'ordens',       icone: '🛠️', titulo: 'Ordens de Serviço' },
    { id: 'vendas',       icone: '🧾', titulo: 'Histórico de Vendas' },
    { id: 'relatorios',   icone: '📈', titulo: 'Relatórios' },
    { id: 'usuarios',     icone: '🔐', titulo: 'Usuários' }
  ],

  // ----------------------------------------------------------------------
  //  INICIALIZAÇÃO
  // ----------------------------------------------------------------------

  /** Ponto de partida: carrega a configuração, monta a apresentação e restaura a sessão (se houver). */
  async iniciar() {
    try {
      Object.assign(Config, await Api.get('/config'));
    } catch (e) {
      document.body.innerHTML = `<p style="padding:40px;color:#f0625f">${UI.esc(e.message)}</p>`;
      return;
    }
    // Em modo de uso real (sem dados de exemplo) os botões de acesso de demonstração não aparecem
    if (!Config.demo) document.querySelector('.demo-acessos').classList.add('oculto');
    this.preencherEmpresa();
    this.montarMatrizApresentacao();
    this.ligarEventos();

    // Se já havia login nesta aba (token guardado), tenta voltar direto para o sistema
    if (Api.token) {
      try {
        const dados = await Api.get('/eu');
        this.entrar(dados);
        return;
      } catch (e) {
        Api.definirToken('');
      }
    }
    this.mostrarApresentacao();
  },

  /** Troca todos os textos marcados com data-emp="campo" pelos dados da empresa. */
  preencherEmpresa() {
    const e = Config.empresa;
    document.querySelectorAll('[data-emp]').forEach(el => { el.textContent = e[el.dataset.emp] ?? ''; });
    document.title = e.nome + ' PDV';
  },

  /** Desenha, na apresentação, a MATRIZ de permissões (perfis nas linhas, módulos nas colunas). */
  montarMatrizApresentacao() {
    const nomes = {};
    this.MENU.forEach(m => { nomes[m.id] = m.titulo; });
    const cab = Config.modulos.map(m => `<th style="text-align:center">${UI.esc(nomes[m] || m)}</th>`).join('');
    const linhas = Config.perfis.map((perfil, i) => `
      <tr>
        <td><b>${UI.esc(perfil)}</b></td>
        ${Config.permissoes[i].map(ok => `<td style="text-align:center">${ok ? '<span style="color:var(--grama)">✔</span>' : '<span style="color:var(--texto-suave)">—</span>'}</td>`).join('')}
      </tr>`).join('');
    document.getElementById('apres-matriz').innerHTML =
      `<table class="tabela"><thead><tr><th>Perfil</th>${cab}</tr></thead><tbody>${linhas}</tbody></table>`;
  },

  /** Liga os botões da apresentação, do login e do menu. */
  ligarEventos() {
    const abrirLogin = () => {
      document.getElementById('tela-login').classList.remove('oculto');
      document.getElementById('login-usuario').focus();
    };
    ['btn-abrir-login', 'btn-hero-login', 'btn-final-login'].forEach(id =>
      document.getElementById(id).addEventListener('click', abrirLogin));
    document.getElementById('btn-fechar-login').addEventListener('click', () =>
      document.getElementById('tela-login').classList.add('oculto'));

    // Botões de acesso de demonstração preenchem usuário e senha
    document.querySelectorAll('[data-demo]').forEach(b => b.addEventListener('click', () => {
      const [login, senha] = b.dataset.demo.split('|');
      document.getElementById('login-usuario').value = login;
      document.getElementById('login-senha').value = senha;
    }));

    document.getElementById('form-login').addEventListener('submit', (e) => { e.preventDefault(); this.fazerLogin(); });
    document.getElementById('btn-sair').addEventListener('click', () => this.sair());
  },

  // ----------------------------------------------------------------------
  //  LOGIN / LOGOUT
  // ----------------------------------------------------------------------

  /** Envia usuário e senha ao servidor. */
  async fazerLogin() {
    const erro = document.getElementById('login-erro');
    erro.textContent = '';
    try {
      const dados = await Api.post('/login', {
        login: document.getElementById('login-usuario').value.trim(),
        senha: document.getElementById('login-senha').value
      });
      Api.definirToken(dados.token);
      document.getElementById('form-login').reset();
      this.entrar(dados);
    } catch (e) {
      erro.textContent = e.message;
    }
  },

  /** Mostra o sistema para o usuário que acabou de entrar. */
  entrar(dados) {
    this.usuario = dados.usuario;
    this.modulos = dados.modulos;

    document.getElementById('tela-apresentacao').classList.add('oculto');
    document.getElementById('tela-login').classList.add('oculto');
    document.getElementById('app').classList.remove('oculto');

    const inicial = this.usuario.nome.trim().charAt(0).toUpperCase();
    document.getElementById('usuario-chip').innerHTML = `
      <div class="avatar">${UI.esc(inicial)}</div>
      <div><b>${UI.esc(this.usuario.nome)}</b><small>${UI.esc(this.usuario.perfil)}</small></div>`;
    document.getElementById('topo-direita').textContent =
      new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' });

    // Monta o menu apenas com os módulos que o perfil pode acessar
    const itens = this.MENU.filter(m => this.modulos.includes(m.id));
    document.getElementById('menu-itens').innerHTML = itens.map(m =>
      `<a href="#" class="item-menu" data-modulo="${m.id}"><span class="ic">${m.icone}</span>${UI.esc(m.titulo)}</a>`).join('');
    document.querySelectorAll('.item-menu').forEach(a => a.addEventListener('click', (ev) => {
      ev.preventDefault();
      this.navegar(a.dataset.modulo);
    }));

    // Abre a última página visitada (ou a primeira liberada)
    const ultima = sessionStorage.getItem('pagina');
    this.navegar(itens.some(m => m.id === ultima) ? ultima : itens[0].id);
  },

  /** Encerra a sessão e volta para a apresentação. */
  async sair() {
    try { await Api.post('/logout'); } catch (e) { /* sem problema: o token some do mesmo jeito */ }
    Api.definirToken('');
    sessionStorage.removeItem('pagina');
    this.mostrarApresentacao();
  },

  /** Volta para a tela de apresentação (também usada quando a sessão expira). */
  mostrarApresentacao() {
    this.usuario = null;
    document.getElementById('app').classList.add('oculto');
    document.getElementById('tela-login').classList.add('oculto');
    document.getElementById('tela-apresentacao').classList.remove('oculto');
    window.scrollTo(0, 0);
  },

  // ----------------------------------------------------------------------
  //  NAVEGAÇÃO ENTRE PÁGINAS
  // ----------------------------------------------------------------------

  /** Abre uma página: marca o item do menu, ajusta o título e pede à página para se desenhar. */
  async navegar(modulo) {
    const info = this.MENU.find(m => m.id === modulo);
    sessionStorage.setItem('pagina', modulo);
    document.querySelectorAll('.item-menu').forEach(a => a.classList.toggle('ativo', a.dataset.modulo === modulo));
    document.getElementById('titulo-pagina').textContent = info.icone + '  ' + info.titulo;

    const el = document.getElementById('conteudo');
    el.innerHTML = '<p class="sub">Carregando...</p>';
    try {
      await Paginas[modulo](el);
      el.classList.remove('pagina-entrada');
      void el.offsetWidth;                       // reinicia a animação de entrada
      el.classList.add('pagina-entrada');
    } catch (e) {
      el.innerHTML = `<div class="card"><h3>Ops!</h3><p>${UI.esc(e.message)}</p></div>`;
    }
  }
};

// Quando a página terminar de carregar, inicia o sistema
document.addEventListener('DOMContentLoaded', () => App.iniciar());

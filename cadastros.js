/* ==========================================================================
   cadastros.js - Gerador genérico de telas de cadastro (CRUD)
   Uma única função monta a tela de Produtos, Clientes, Fornecedores,
   Ordens de Serviço e Usuários: tabela + busca + formulário em janela modal.
   Cada tela é descrita por um objeto de configuração (veja paginas.js).
   ========================================================================== */

/**
 * Cria o desenhista de uma página de cadastro.
 *
 * cfg = {
 *   rota:      '/produtos'                       caminho da API
 *   singular:  'Produto'                         usado nos títulos e mensagens
 *   colunas:   [{ titulo, valor:(registro, contexto)=>html, classe }]
 *   campos:    [{ nome, label, tipo, obrigatorio, opcoes, largo, dica, numerico }]
 *   busca:     (registro)=>texto                 texto onde a pesquisa procura
 *   padrao:    { ... }                           valores iniciais de um novo registro
 *   contexto:  async ()=>objeto                  dados extras (ex.: lista de fornecedores)
 *   feminino:  true para substantivos femininos ("Nova ordem de serviço")
 *   podeExcluir: true/false
 * }
 */
function criarCadastro(cfg) {
  return async function desenhar(el) {
    // Carrega a lista e (se houver) os dados extras da tela
    let registros = await Api.get(cfg.rota);
    const contexto = cfg.contexto ? await cfg.contexto() : {};
    let termo = '';

    // Concordância de gênero em português: "Novo produto" / "Nova ordem de serviço"
    const novo_ = cfg.feminino ? 'Nova' : 'Novo';
    const o_ = cfg.feminino ? 'a' : 'o';

    // Estrutura da página
    el.innerHTML = `
      <div class="barra-ferramentas">
        <div class="busca"><input type="search" id="cad-busca" placeholder="Buscar ${UI.esc(cfg.singular.toLowerCase())}..."></div>
        <button class="btn primario" id="cad-novo">＋ ${novo_} ${UI.esc(cfg.singular.toLowerCase())}</button>
      </div>
      <div class="tabela-wrap"><table class="tabela">
        <thead><tr>${cfg.colunas.map(c => `<th class="${c.classe || ''}">${UI.esc(c.titulo)}</th>`).join('')}<th></th></tr></thead>
        <tbody id="cad-corpo"></tbody>
      </table></div>
      <p class="sub" style="margin-top:10px" id="cad-contagem"></p>`;

    const corpo = el.querySelector('#cad-corpo');

    /** Redesenha as linhas da tabela aplicando o filtro de busca. */
    function desenharLinhas() {
      const filtrados = registros.filter(r => cfg.busca(r, contexto).toLowerCase().includes(termo));
      if (filtrados.length === 0) {
        corpo.innerHTML = `<tr><td class="vazio" colspan="${cfg.colunas.length + 1}">Nenhum registro encontrado.</td></tr>`;
      } else {
        corpo.innerHTML = filtrados.map(r => `
          <tr>
            ${cfg.colunas.map(c => `<td class="${c.classe || ''}">${c.valor(r, contexto)}</td>`).join('')}
            <td class="acoes">
              <button class="btn pequeno" data-acao="editar" data-id="${r.id}">✏️ Editar</button>
              ${cfg.podeExcluir === false ? '' : `<button class="btn pequeno perigo" data-acao="excluir" data-id="${r.id}">🗑️</button>`}
            </td>
          </tr>`).join('');
      }
      el.querySelector('#cad-contagem').textContent = `${filtrados.length} de ${registros.length} registro(s)`;
    }

    /** Recarrega os dados do servidor e redesenha. */
    async function recarregar() {
      registros = await Api.get(cfg.rota);
      desenharLinhas();
    }

    /** Abre o formulário (novo ou edição). */
    async function abrirFormulario(registro) {
      const novo = !registro;
      const valores = registro || (cfg.padrao || {});

      // Resolve as opções dos campos "select" (podem vir de uma função assíncrona)
      const campos = [];
      for (const c of cfg.campos) {
        const copia = { ...c };
        if (c.tipo === 'select') {
          copia.listaOpcoes = typeof c.opcoes === 'function' ? await c.opcoes(contexto) : c.opcoes;
        }
        campos.push(copia);
      }

      UI.modal({
        titulo: (novo ? novo_ + ' ' : 'Editar ') + cfg.singular.toLowerCase(),
        corpo: `<form class="form-grid" id="form-cad" autocomplete="off">${campos.map(c => campoHTML(c, valores[c.nome], novo)).join('')}</form>`,
        botoes: [
          { texto: 'Cancelar' },
          {
            texto: 'Salvar', classe: 'primario',
            aoClicar: async ({ el: janela }) => {
              const form = janela.querySelector('#form-cad');
              if (!form.reportValidity()) return false;     // campos obrigatórios vazios: mantém aberto
              const dados = lerFormulario(form, campos);
              try {
                if (novo) await Api.post(cfg.rota, dados);
                else await Api.put(cfg.rota + '/' + registro.id, dados);
                UI.toast(cfg.singular + (novo ? ' cadastrad' : ' atualizad') + o_ + ' com sucesso!');
                await recarregar();
              } catch (e) {
                UI.toast(e.message, 'erro');
                return false;
              }
            }
          }
        ]
      });
    }

    // ---- Eventos ----
    el.querySelector('#cad-busca').addEventListener('input', (e) => { termo = e.target.value.toLowerCase(); desenharLinhas(); });
    el.querySelector('#cad-novo').onclick = () => abrirFormulario(null);
    corpo.addEventListener('click', async (e) => {
      const botao = e.target.closest('button[data-acao]');
      if (!botao) return;
      const registro = registros.find(r => r.id === Number(botao.dataset.id));
      if (botao.dataset.acao === 'editar') {
        abrirFormulario(registro);
      } else if (botao.dataset.acao === 'excluir') {
        if (await UI.confirmar(`Excluir ${cfg.feminino ? 'esta' : 'este'} ${cfg.singular.toLowerCase()}? Essa ação não pode ser desfeita.`, 'Excluir')) {
          try {
            await Api.del(cfg.rota + '/' + registro.id);
            UI.toast(cfg.singular + ' excluíd' + o_ + '.');
            await recarregar();
          } catch (erro) {
            UI.toast(erro.message, 'erro');
          }
        }
      }
    });

    desenharLinhas();
  };
}

/** Monta o HTML de um campo do formulário conforme o seu tipo. */
function campoHTML(c, valor, novo) {
  const id = 'c_' + c.nome;
  const req = c.obrigatorio ? 'required' : '';
  const v = valor ?? '';
  let controle;

  if (c.tipo === 'select') {
    controle = `<select id="${id}" ${req}>${c.listaOpcoes.map(o =>
      `<option value="${UI.esc(o.v)}" ${String(o.v) === String(v) ? 'selected' : ''}>${UI.esc(o.t)}</option>`).join('')}</select>`;
  } else if (c.tipo === 'textarea') {
    controle = `<textarea id="${id}" rows="3" ${req}>${UI.esc(v)}</textarea>`;
  } else if (c.tipo === 'checkbox') {
    controle = `<label class="check"><input type="checkbox" id="${id}" ${valor === undefined || valor ? 'checked' : ''}><span>${UI.esc(c.rotuloCheck || 'Sim')}</span></label>`;
  } else {
    const tipos = { texto: 'text', email: 'email', tel: 'tel', inteiro: 'number', dinheiro: 'number', senha: 'password' };
    const extra = c.tipo === 'dinheiro' ? 'step="0.01" min="0"' : c.tipo === 'inteiro' ? 'step="1" min="0"' : '';
    const dica = c.tipo === 'senha' && !novo ? 'Deixe em branco para manter a senha atual' : (c.dica || '');
    controle = `<input id="${id}" type="${tipos[c.tipo || 'texto']}" value="${c.tipo === 'senha' ? '' : UI.esc(v)}" ${extra} ${req} placeholder="${UI.esc(dica)}" ${c.tipo === 'senha' ? 'autocomplete="new-password"' : ''}>`;
  }
  const obrigatorio = c.obrigatorio && !(c.tipo === 'senha' && !novo) ? ' *' : '';
  const exigirSenha = (c.tipo === 'senha' && novo) ? ' *' : '';
  return `<div class="campo ${c.largo ? 'largo' : ''}"><label for="${id}">${UI.esc(c.label)}${c.tipo === 'senha' ? exigirSenha : obrigatorio}</label>${controle}</div>`;
}

/** Lê os valores digitados no formulário e converte cada um para o tipo certo. */
function lerFormulario(form, campos) {
  const dados = {};
  for (const c of campos) {
    const el = form.querySelector('#c_' + c.nome);
    if (c.tipo === 'checkbox') dados[c.nome] = el.checked;
    else if (c.tipo === 'inteiro') dados[c.nome] = parseInt(el.value || '0', 10);
    else if (c.tipo === 'dinheiro') dados[c.nome] = parseFloat(el.value || '0');
    else if (c.numerico) dados[c.nome] = Number(el.value);
    else dados[c.nome] = el.value;
  }
  return dados;
}

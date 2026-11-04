package pdv.modelo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import pdv.util.Json;

/**
 * Ordem de Serviço (OS) da assistência técnica: registra o aparelho do cliente,
 * o defeito relatado, o andamento do conserto e o valor cobrado.
 */
public class OrdemServico implements Entidade {

    /** VETOR com as etapas (status) pelas quais uma OS passa. */
    public static final String[] STATUS = {
        "Aberta", "Em análise", "Aguardando peça", "Em reparo", "Pronta", "Entregue", "Cancelada"
    };

    // ---- Atributos ----
    private int id;
    private int clienteId;                 // 0 = cliente avulso (sem cadastro)
    private String clienteNome = "";
    private String aparelho = "";          // ex.: "Smartphone 64GB", "Notebook 15 pol."
    private String defeito = "";           // problema relatado pelo cliente
    private String tecnico = "";
    private String status = STATUS[0];
    private double valorServico;           // mão de obra
    private double valorPecas;             // peças utilizadas
    private String observacoes = "";
    private String dataEntrada = "";       // ISO: 2026-10-26T14:30:00
    private String dataSaida = "";         // preenchida quando a OS é entregue

    /** Cria uma OS a partir de um Map. */
    public static OrdemServico fromMap(Map<String, Object> m) {
        OrdemServico o = new OrdemServico();
        o.id = Json.inteiro(m, "id");
        o.clienteId = Json.inteiro(m, "clienteId");
        o.clienteNome = Json.texto(m, "clienteNome");
        o.aparelho = Json.texto(m, "aparelho");
        o.defeito = Json.texto(m, "defeito");
        o.tecnico = Json.texto(m, "tecnico");
        o.status = Json.texto(m, "status");
        o.valorServico = Json.numero(m, "valorServico");
        o.valorPecas = Json.numero(m, "valorPecas");
        o.observacoes = Json.texto(m, "observacoes");
        o.dataEntrada = Json.texto(m, "dataEntrada");
        o.dataSaida = Json.texto(m, "dataSaida");
        if (o.status.isEmpty()) {
            o.status = STATUS[0];
        }
        return o;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("clienteId", clienteId);
        m.put("clienteNome", clienteNome);
        m.put("aparelho", aparelho);
        m.put("defeito", defeito);
        m.put("tecnico", tecnico);
        m.put("status", status);
        m.put("valorServico", valorServico);
        m.put("valorPecas", valorPecas);
        m.put("observacoes", observacoes);
        m.put("dataEntrada", dataEntrada);
        m.put("dataSaida", dataSaida);
        return m;
    }

    /** Na API, acrescentamos o total (serviço + peças) como campo calculado. */
    @Override
    public Map<String, Object> paraApi() {
        Map<String, Object> m = toMap();
        m.put("total", total());
        return m;
    }

    @Override
    public void validar() {
        if (aparelho.isBlank()) {
            throw new IllegalArgumentException("Informe o aparelho que está em atendimento.");
        }
        if (defeito.isBlank()) {
            throw new IllegalArgumentException("Descreva o defeito relatado pelo cliente.");
        }
        if (!Arrays.asList(STATUS).contains(status)) {
            throw new IllegalArgumentException("Status da ordem de serviço inválido.");
        }
        if (valorServico < 0 || valorPecas < 0) {
            throw new IllegalArgumentException("Os valores não podem ser negativos.");
        }
    }

    /** Valor total da OS: mão de obra + peças. */
    public double total() {
        return Venda.arredondar(valorServico + valorPecas);
    }

    /** @return true enquanto a OS ainda está em andamento (não entregue nem cancelada). */
    public boolean emAberto() {
        return !"Entregue".equals(status) && !"Cancelada".equals(status);
    }

    // ---- Getters e Setters ----
    @Override public int getId() { return id; }
    @Override public void setId(int id) { this.id = id; }
    public int getClienteId() { return clienteId; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }
    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }
    public String getAparelho() { return aparelho; }
    public void setAparelho(String aparelho) { this.aparelho = aparelho; }
    public String getDefeito() { return defeito; }
    public void setDefeito(String defeito) { this.defeito = defeito; }
    public String getTecnico() { return tecnico; }
    public void setTecnico(String tecnico) { this.tecnico = tecnico; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getValorServico() { return valorServico; }
    public void setValorServico(double valorServico) { this.valorServico = valorServico; }
    public double getValorPecas() { return valorPecas; }
    public void setValorPecas(double valorPecas) { this.valorPecas = valorPecas; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public String getDataEntrada() { return dataEntrada; }
    public void setDataEntrada(String dataEntrada) { this.dataEntrada = dataEntrada; }
    public String getDataSaida() { return dataSaida; }
    public void setDataSaida(String dataSaida) { this.dataSaida = dataSaida; }
}

package com.oficina.tech_challenge.domain.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrdemServico {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private long versao;

    // Nullable for pre-migration orders; no branch is guessed for historical data.
    private UUID filialId;

    @ManyToOne
    private Cliente cliente;

    @ManyToOne
    private Veiculo veiculo;

    @Enumerated(EnumType.STRING)
    private StatusOrdemServico status;

    @Column(length = 4000)
    private String diagnostico;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataInicioExecucao;
    private LocalDateTime dataFinalizacao;

    @OneToMany(mappedBy = "ordemServico", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HistoricoStatusOrdemServico> historicoStatus = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ordem_servico_id")
    private List<ItemServico> servicos = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ordem_servico_id")
    private List<ItemPeca> pecas = new ArrayList<>();

    public OrdemServico(Cliente cliente, Veiculo veiculo) {
        this.cliente = cliente;
        this.veiculo = veiculo;
        this.status = StatusOrdemServico.RECEBIDA;
        this.dataCriacao = LocalDateTime.now();
        this.historicoStatus.add(new HistoricoStatusOrdemServico(this, null, this.status, "ABERTURA", null));
    }

    public void identificarFilial(UUID filial) {
        if (filial == null) throw new IllegalArgumentException("Filial é obrigatória");
        if (filialId != null && !filialId.equals(filial))
            throw new IllegalStateException("Filial da OS não pode ser alterada");
        filialId = filial;
    }

    public void registrarHistorico(StatusOrdemServico anterior, String origem, String correlationId) {
        if (anterior != this.status) historicoStatus.add(new HistoricoStatusOrdemServico(this, anterior, status, origem, correlationId));
    }

    public void adicionarServico(Servico servico) {
        if (status != StatusOrdemServico.RECEBIDA && status != StatusOrdemServico.DIAGNOSTICO) {
            throw new IllegalStateException("Não é possível adicionar serviços nesta fase da OS");
        }
        this.servicos.add(new ItemServico(servico));
    }

    public void adicionarPeca(Peca peca, int quantidade) {
        if (status != StatusOrdemServico.RECEBIDA && status != StatusOrdemServico.DIAGNOSTICO) {
            throw new IllegalStateException("Não é possível adicionar peças nesta fase da OS");
        }
        this.pecas.add(new ItemPeca(peca, quantidade));
    }

    public void adicionarServicoCatalogo(UUID id, String descricao, BigDecimal preco) {
        if (status != StatusOrdemServico.RECEBIDA && status != StatusOrdemServico.DIAGNOSTICO)
            throw new IllegalStateException("Não é possível adicionar serviços nesta fase da OS");
        servicos.add(new ItemServico(id, descricao, preco));
    }

    public void adicionarPecaCatalogo(UUID id, String descricao, BigDecimal preco, int quantidade) {
        if (status != StatusOrdemServico.RECEBIDA && status != StatusOrdemServico.DIAGNOSTICO)
            throw new IllegalStateException("Não é possível adicionar peças nesta fase da OS");
        pecas.add(new ItemPeca(id, descricao, preco, quantidade));
    }

    public void registrarDiagnostico(String diagnostico) {
        this.diagnostico = diagnostico;
        if (this.status == StatusOrdemServico.RECEBIDA) {
            this.status = StatusOrdemServico.DIAGNOSTICO;
        }
    }

    public void gerarOrcamento() {
        if (this.status != StatusOrdemServico.DIAGNOSTICO) {
            throw new IllegalStateException("OS deve estar em diagnóstico para gerar orçamento");
        }
        this.status = StatusOrdemServico.AGUARDANDO_APROVACAO;
    }

    public void aprovar() {
        if (this.status != StatusOrdemServico.AGUARDANDO_APROVACAO) {
            throw new IllegalStateException("Somente OS aguardando aprovação podem ser aprovadas");
        }
        this.status = StatusOrdemServico.EXECUCAO;
        this.dataInicioExecucao = LocalDateTime.now();
    }

    public void aprovarAguardandoPagamento() {
        if (this.status != StatusOrdemServico.AGUARDANDO_APROVACAO)
            throw new IllegalStateException("Somente OS aguardando aprovação podem ser aprovadas");
        this.status = StatusOrdemServico.AGUARDANDO_PAGAMENTO;
    }

    public void iniciarExecucaoConfirmada() {
        if (status == StatusOrdemServico.EXECUCAO) return;
        if (status != StatusOrdemServico.AGUARDANDO_PAGAMENTO)
            throw new IllegalStateException("Execução confirmada requer OS aguardando pagamento");
        status = StatusOrdemServico.EXECUCAO;
        dataInicioExecucao = LocalDateTime.now();
    }

    public void recusar() {
        if (this.status != StatusOrdemServico.AGUARDANDO_APROVACAO) {
            throw new IllegalStateException("Somente OS aguardando aprovação podem ser recusadas");
        }
        this.status = StatusOrdemServico.RECUSADA;
        this.dataFinalizacao = LocalDateTime.now();
    }

    public void atualizarStatus(StatusOrdemServico novoStatus) {
        switch (novoStatus) {
            case DIAGNOSTICO -> {
                if (this.status != StatusOrdemServico.RECEBIDA && this.status != StatusOrdemServico.DIAGNOSTICO) {
                    throw new IllegalStateException("OS só pode entrar em diagnóstico a partir de RECEBIDA");
                }
                registrarDiagnostico(this.diagnostico == null ? "Atualização externa" : this.diagnostico);
            }
            case AGUARDANDO_APROVACAO -> gerarOrcamento();
            case AGUARDANDO_PAGAMENTO -> throw new IllegalStateException("Aguardando pagamento depende da aprovação de orçamento");
            case EXECUCAO -> aprovar();
            case FINALIZADA -> finalizar();
            case ENTREGUE -> entregar();
            case RECUSADA -> recusar();
            case RECEBIDA -> {
                if (this.status != StatusOrdemServico.RECEBIDA) {
                    throw new IllegalStateException("Não é possível retornar uma OS para RECEBIDA");
                }
            }
        }
    }

    public void finalizar() {
        if (this.status != StatusOrdemServico.EXECUCAO) {
            throw new IllegalStateException("Somente OS em execução podem ser finalizadas");
        }
        this.status = StatusOrdemServico.FINALIZADA;
        this.dataFinalizacao = LocalDateTime.now();
    }

    public void entregar() {
        if (this.status != StatusOrdemServico.FINALIZADA) {
            throw new IllegalStateException("Somente OS finalizadas podem ser entregues");
        }
        this.status = StatusOrdemServico.ENTREGUE;
    }

    public BigDecimal getValorTotal() {
        BigDecimal totalServicos = servicos.stream()
                .map(ItemServico::getPrecoAplicado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPecas = pecas.stream()
                .map(ItemPeca::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalServicos.add(totalPecas);
    }
}

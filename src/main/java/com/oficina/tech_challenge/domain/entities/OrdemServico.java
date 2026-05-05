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

    @ManyToOne
    private Cliente cliente;

    @ManyToOne
    private Veiculo veiculo;

    @Enumerated(EnumType.STRING)
    private StatusOrdemServico status;

    private String diagnostico;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataInicioExecucao;
    private LocalDateTime dataFinalizacao;

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

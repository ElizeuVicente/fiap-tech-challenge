package com.oficina.tech_challenge.domain.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class HistoricoStatusOrdemServico {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;
    @Enumerated(EnumType.STRING) private StatusOrdemServico statusAnterior;
    @Enumerated(EnumType.STRING) private StatusOrdemServico novoStatus;
    private LocalDateTime dataHora;
    private String origem;
    private String correlationId;
    HistoricoStatusOrdemServico(OrdemServico ordemServico, StatusOrdemServico anterior, StatusOrdemServico novo,
            String origem, String correlationId) {
        this.ordemServico = ordemServico;
        this.statusAnterior = anterior; this.novoStatus = novo; this.origem = origem;
        this.correlationId = correlationId; this.dataHora = LocalDateTime.now();
    }
}

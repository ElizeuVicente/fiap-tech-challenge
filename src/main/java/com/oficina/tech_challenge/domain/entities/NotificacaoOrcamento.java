package com.oficina.tech_challenge.domain.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class NotificacaoOrcamento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    private OrdemServico ordemServico;

    @Enumerated(EnumType.STRING)
    private DecisaoOrcamento decisao;

    private String origem;
    private LocalDateTime dataHora;

    @Column(unique = true, nullable = false)
    private String identificadorExterno;

    public NotificacaoOrcamento(OrdemServico ordemServico, DecisaoOrcamento decisao, String origem,
            LocalDateTime dataHora, String identificadorExterno) {
        if (identificadorExterno == null || identificadorExterno.isBlank()) {
            throw new IllegalArgumentException("Identificador externo é obrigatório");
        }
        this.ordemServico = ordemServico;
        this.decisao = decisao;
        this.origem = origem;
        this.dataHora = dataHora == null ? LocalDateTime.now() : dataHora;
        this.identificadorExterno = identificadorExterno;
    }
}

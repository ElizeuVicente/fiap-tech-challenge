package com.oficina.tech_challenge.application.dtos;

import com.oficina.tech_challenge.domain.entities.DecisaoOrcamento;

import java.time.LocalDateTime;

public record NotificacaoOrcamentoCommand(
        DecisaoOrcamento decisao,
        String origem,
        LocalDateTime dataHora,
        String identificadorExterno
) {
}

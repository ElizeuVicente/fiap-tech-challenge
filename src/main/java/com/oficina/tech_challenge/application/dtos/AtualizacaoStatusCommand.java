package com.oficina.tech_challenge.application.dtos;

import com.oficina.tech_challenge.domain.entities.StatusOrdemServico;

public record AtualizacaoStatusCommand(
        StatusOrdemServico novoStatus,
        String origem
) {
}

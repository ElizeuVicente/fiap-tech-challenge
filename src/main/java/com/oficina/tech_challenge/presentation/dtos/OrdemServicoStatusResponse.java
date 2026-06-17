package com.oficina.tech_challenge.presentation.dtos;

import com.oficina.tech_challenge.domain.entities.OrdemServico;
import com.oficina.tech_challenge.domain.entities.StatusOrdemServico;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrdemServicoStatusResponse(
        UUID id,
        StatusOrdemServico status,
        String descricao,
        LocalDateTime dataCriacao,
        String proximaAcaoEsperada
) {
    public static OrdemServicoStatusResponse from(OrdemServico os) {
        return new OrdemServicoStatusResponse(
                os.getId(),
                os.getStatus(),
                descricao(os.getStatus()),
                os.getDataCriacao(),
                proximaAcao(os.getStatus()));
    }

    private static String descricao(StatusOrdemServico status) {
        return switch (status) {
            case RECEBIDA -> "Recebida";
            case DIAGNOSTICO -> "Em diagnóstico";
            case AGUARDANDO_APROVACAO -> "Aguardando Aprovação";
            case EXECUCAO -> "Em execução";
            case FINALIZADA -> "Finalizada";
            case ENTREGUE -> "Entregue";
            case RECUSADA -> "Orçamento recusado";
        };
    }

    private static String proximaAcao(StatusOrdemServico status) {
        return switch (status) {
            case RECEBIDA -> "Registrar diagnóstico";
            case DIAGNOSTICO -> "Gerar orçamento";
            case AGUARDANDO_APROVACAO -> "Aprovação ou recusa do orçamento pelo cliente";
            case EXECUCAO -> "Finalizar execução dos serviços";
            case FINALIZADA -> "Entregar veículo ao cliente";
            case ENTREGUE, RECUSADA -> "Nenhuma ação operacional pendente";
        };
    }
}

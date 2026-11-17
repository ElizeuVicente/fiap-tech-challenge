package com.oficina.tech_challenge.infrastructure.saga;

import com.oficina.tech_challenge.application.dtos.NotificacaoOrcamentoCommand;
import com.oficina.tech_challenge.application.services.GerenciadorOrdemServico;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates a failed legacy OS transition so the coordinator can persist compensation. */
@Service
public class BillingOrderDecisionApplier {
    private final GerenciadorOrdemServico ordens;
    public BillingOrderDecisionApplier(GerenciadorOrdemServico ordens) { this.ordens = ordens; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apply(UUID orderId, NotificacaoOrcamentoCommand command) {
        ordens.processarDecisaoBilling(orderId, command);
    }
}

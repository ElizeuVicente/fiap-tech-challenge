package com.oficina.tech_challenge.application.interfaces;

import java.math.BigDecimal;
import java.util.UUID;

/** Consulta catálogo por filial sem acesso ao banco da Execução. */
public interface ExecutionCatalogQuery {
    record Item(UUID branchId, UUID referenceId, String description, BigDecimal unitPrice) { }
    Item part(UUID branchId, UUID partId);
    Item service(UUID branchId, UUID serviceId);
}

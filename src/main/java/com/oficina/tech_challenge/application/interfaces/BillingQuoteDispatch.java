package com.oficina.tech_challenge.application.interfaces;

import java.math.BigDecimal;
import java.util.UUID;

public interface BillingQuoteDispatch {
    record Item(String type, UUID referenceId, int quantity, BigDecimal unitPrice) { }
    void schedule(UUID orderId, BigDecimal amount);
    void schedule(UUID orderId, BigDecimal amount, java.util.List<Item> items);
    void schedule(UUID orderId, UUID branchId, BigDecimal amount, java.util.List<Item> items);
}

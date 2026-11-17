package com.oficina.tech_challenge.application.interfaces;

import java.util.Optional;
import java.util.UUID;

/** Read-only boundary: the OS never reads the Billing database. */
public interface BillingCheckoutQuery {
    record Checkout(UUID attemptId, String state, String checkoutUrl, String sandboxUrl) { }
    Optional<Checkout> findByOrderId(UUID orderId);
}

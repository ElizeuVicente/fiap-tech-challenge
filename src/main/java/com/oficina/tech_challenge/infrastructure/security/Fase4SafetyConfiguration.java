package com.oficina.tech_challenge.infrastructure.security;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Prevents distributed integrations from starting with legacy approval behavior. */
@Configuration
class Fase4SafetyConfiguration {
    @Bean SmartInitializingSingleton validateFase4PaymentGate(
            @Value("${oficina.fase4.payment-gate:false}") boolean gate,
            @Value("${oficina.billing-consumer.enabled:false}") boolean billingConsumer,
            @Value("${oficina.billing-quote.enabled:false}") boolean billingQuote,
            @Value("${oficina.execution.enabled:false}") boolean execution,
            @Value("${oficina.execution-catalog.enabled:false}") boolean executionCatalog,
            @Value("${oficina.stock-saga.enabled:false}") boolean stockSaga,
            @Value("${oficina.execution.branch-id:}") String executionBranch,
            @Value("${oficina.billing-quote.branch-id:}") String billingBranch) {
        return () -> {
            if (!gate && (billingConsumer || billingQuote || execution || executionCatalog || stockSaga))
                throw new IllegalStateException("Fase 4 integrations require oficina.fase4.payment-gate=true");
            if (executionCatalog && !(execution && billingQuote && billingConsumer && stockSaga))
                throw new IllegalStateException("Execution catalog requires execution, billing and stock saga integrations");
            if (stockSaga && !(billingConsumer && billingQuote && execution))
                throw new IllegalStateException("Stock saga requires billing consumer, itemized billing and execution integrations");
            if (billingQuote && execution) {
                try {
                    if (!java.util.UUID.fromString(executionBranch).equals(java.util.UUID.fromString(billingBranch)))
                        throw new IllegalStateException("Billing and execution fallback branches must match");
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalStateException("Billing and execution fallback branches must be valid UUIDs", invalid);
                }
            }
        };
    }
}

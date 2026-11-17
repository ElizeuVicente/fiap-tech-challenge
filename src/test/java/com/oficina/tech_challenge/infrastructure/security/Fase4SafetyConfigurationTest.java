package com.oficina.tech_challenge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class Fase4SafetyConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Fase4SafetyConfiguration.class);

    @Test void eachDistributedIntegrationRequiresPaymentGate() {
        for (String feature : new String[]{"oficina.billing-consumer.enabled", "oficina.billing-quote.enabled", "oficina.execution.enabled"}) {
            context.withPropertyValues(feature + "=true").run(result -> assertThat(result).hasFailed());
            context.withPropertyValues(feature + "=true", "oficina.fase4.payment-gate=true").run(result -> assertThat(result).hasNotFailed());
        }
    }
    @Test void legacyOnlyCanStartWithoutDistributedIntegrations() {
        context.run(result -> assertThat(result).hasNotFailed());
    }
    @Test void catalogRequiresCompleteDistributedPaymentFlow() {
        context.withPropertyValues("oficina.execution-catalog.enabled=true").run(result -> assertThat(result).hasFailed());
        context.withPropertyValues("oficina.execution-catalog.enabled=true", "oficina.fase4.payment-gate=true")
                .run(result -> assertThat(result).hasFailed());
        context.withPropertyValues("oficina.execution-catalog.enabled=true", "oficina.fase4.payment-gate=true",
                "oficina.execution.enabled=true", "oficina.billing-quote.enabled=true", "oficina.billing-consumer.enabled=true",
                "oficina.stock-saga.enabled=true", "oficina.execution.branch-id=11111111-1111-4111-8111-111111111111",
                "oficina.billing-quote.branch-id=11111111-1111-4111-8111-111111111111").run(result -> assertThat(result).hasNotFailed());
    }
    @Test void distributedFallbackBranchesMustMatch() {
        context.withPropertyValues("oficina.fase4.payment-gate=true", "oficina.execution.enabled=true", "oficina.billing-quote.enabled=true",
                "oficina.execution.branch-id=11111111-1111-4111-8111-111111111111",
                "oficina.billing-quote.branch-id=22222222-2222-4222-8222-222222222222")
                .run(result -> assertThat(result).hasFailed());
    }
}

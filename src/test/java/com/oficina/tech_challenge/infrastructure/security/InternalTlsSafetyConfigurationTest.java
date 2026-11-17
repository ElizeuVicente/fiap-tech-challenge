package com.oficina.tech_challenge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InternalTlsSafetyConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(InternalTlsSafetyConfiguration.class);
    @Test void requiresHttpsForEachEnabledIntegration() {
        context.withPropertyValues("oficina.internal-tls.required=true", "oficina.execution.enabled=true",
                "oficina.execution.url=http://execution:8082").run(result -> assertThat(result).hasFailed());
        context.withPropertyValues("oficina.internal-tls.required=true", "oficina.billing-quote.enabled=true",
                "oficina.billing-quote.url=https://billing.example.test", "oficina.execution.enabled=true",
                "oficina.execution.url=https://execution.example.test").run(result -> assertThat(result).hasNotFailed());
        context.withPropertyValues("oficina.internal-tls.required=true", "oficina.billing-quote.enabled=true",
                "oficina.billing-quote.url=https://user:password@billing.example.test").run(result -> assertThat(result).hasFailed());
        context.withPropertyValues("oficina.internal-tls.required=false", "oficina.execution.enabled=true",
                "oficina.execution.url=http://execution:8082").run(result -> assertThat(result).hasNotFailed());
    }
}

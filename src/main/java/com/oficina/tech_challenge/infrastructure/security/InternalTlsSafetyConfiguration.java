package com.oficina.tech_challenge.infrastructure.security;

import java.net.URI;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Optional fail-fast policy for internal HTTP clients; does not configure certificates or mTLS. */
@Configuration
class InternalTlsSafetyConfiguration {
    @Bean SmartInitializingSingleton validateInternalTls(
            @Value("${oficina.internal-tls.required:false}") boolean required,
            @Value("${oficina.execution.enabled:false}") boolean execution,
            @Value("${oficina.execution-catalog.enabled:false}") boolean catalog,
            @Value("${oficina.billing-quote.enabled:false}") boolean billing,
            @Value("${oficina.stock-saga.enabled:false}") boolean stock,
            @Value("${oficina.execution.url:}") String executionUrl,
            @Value("${oficina.billing-quote.url:}") String billingUrl) {
        return () -> {
            if (!required) return;
            if (execution || catalog || stock) requireHttps(executionUrl, "Execution");
            if (billing || stock) requireHttps(billingUrl, "Billing");
        };
    }

    private static void requireHttps(String value, String service) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getQuery() != null)
                throw new IllegalArgumentException("Invalid URL");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(service + " requires a valid HTTPS base URL", invalid);
        }
    }
}

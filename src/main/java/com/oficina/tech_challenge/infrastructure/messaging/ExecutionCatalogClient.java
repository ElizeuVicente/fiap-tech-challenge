package com.oficina.tech_challenge.infrastructure.messaging;

import com.oficina.tech_challenge.application.interfaces.ExecutionCatalogQuery;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "oficina.execution-catalog.enabled", havingValue = "true")
class ExecutionCatalogClient implements ExecutionCatalogQuery {
    private final RestClient client;
    ExecutionCatalogClient(@Value("${oficina.execution.url}") String url,
            @Value("${oficina.execution.service-token}") String token) {
        if (token == null || token.length() < 32) throw new IllegalArgumentException("Execution service token too short");
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token", token).build();
    }
    @Override public Item part(UUID branchId, UUID partId) { return get(branchId, partId, true); }
    @Override public Item service(UUID branchId, UUID serviceId) { return get(branchId, serviceId, false); }

    private Item get(UUID branch, UUID id, boolean part) {
        String path = part ? "/api/branches/{branch}/parts/{item}/catalog" : "/api/branches/{branch}/services/{item}/catalog";
        var response = client.get().uri(path, branch, id).retrieve().body(CatalogResponse.class);
        if (response == null || !branch.toString().equals(response.branchId())
                || !id.toString().equals(part ? response.partId() : response.serviceId())
                || response.description() == null || response.description().isBlank()
                || response.unitPrice() == null || response.unitPrice().signum() <= 0
                || response.unitPrice().scale() > 2 || response.unitPrice().precision() > 15)
            throw new IllegalStateException("Invalid execution catalog response");
        return new Item(branch, id, response.description(), response.unitPrice());
    }
    private record CatalogResponse(String branchId, String partId, String serviceId, String description, BigDecimal unitPrice) { }
}

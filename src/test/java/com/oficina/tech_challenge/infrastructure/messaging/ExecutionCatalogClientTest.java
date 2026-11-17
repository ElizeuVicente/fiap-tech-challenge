package com.oficina.tech_challenge.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecutionCatalogClientTest {
    @Test void validatesCatalogIdentityAndForwardsServiceToken() throws Exception {
        UUID branch = UUID.randomUUID(), part = UUID.randomUUID();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/branches/", exchange -> {
            assertEquals("local-test-token-01234567890123456789", exchange.getRequestHeaders().getFirst("X-Service-Token"));
            String response = "{\"branchId\":\"" + branch + "\",\"partId\":\"" + part + "\",\"description\":\"Filtro\",\"unitPrice\":15.50}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = new ExecutionCatalogClient("http://127.0.0.1:" + server.getAddress().getPort(), "local-test-token-01234567890123456789");
            assertEquals(part, client.part(branch, part).referenceId());
            assertThrows(IllegalStateException.class, () -> client.part(UUID.randomUUID(), part));
            assertThrows(IllegalStateException.class, () -> client.service(branch, UUID.randomUUID()));
        } finally { server.stop(0); }
    }
}

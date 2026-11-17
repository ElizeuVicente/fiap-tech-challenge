package com.oficina.tech_challenge.integration;

import com.oficina.tech_challenge.presentation.handlers.GlobalExceptionHandler;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservabilityHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void configurarRegistro() {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void removerRegistro() {
        Metrics.removeRegistry(registry);
        registry.close();
    }

    @Test
    void registraFalhaDeIntegracaoSemExporSegredo() {
        var request = new MockHttpServletRequest("POST", "/api/ordens-servico/1/orcamento/notificacoes");
        double before = registry.counter("oficina_integracao_falhas_total", "integracao", "webhook").count();
        var response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook secret inválido"), request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(before + 1, registry.counter("oficina_integracao_falhas_total", "integracao", "webhook").count());
    }

    @Test
    void registraFalhaDoPostgresEDeProcessamentoDeOs() {
        var request = new MockHttpServletRequest("POST", "/api/ordens-servico");
        double dbBefore = registry.counter("oficina_integracao_falhas_total", "integracao", "postgres").count();
        double osBefore = registry.counter("oficina_os_falhas_total", "tipo", "DataAccessResourceFailureException").count();
        var response = handler.handleGeneric(new DataAccessResourceFailureException("simulado"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(dbBefore + 1, registry.counter("oficina_integracao_falhas_total", "integracao", "postgres").count());
        assertEquals(osBefore + 1, registry.counter("oficina_os_falhas_total", "tipo", "DataAccessResourceFailureException").count());
    }
}

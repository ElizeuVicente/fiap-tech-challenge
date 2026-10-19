package com.oficina.tech_challenge.integration;

import com.oficina.tech_challenge.presentation.handlers.GlobalExceptionHandler;
import io.micrometer.core.instrument.Metrics;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservabilityHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void registraFalhaDeIntegracaoSemExporSegredo() {
        var request = new MockHttpServletRequest("POST", "/api/ordens-servico/1/orcamento/notificacoes");
        double before = Metrics.counter("oficina_integracao_falhas_total", "integracao", "webhook").count();
        var response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook secret inválido"), request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(before + 1, Metrics.counter("oficina_integracao_falhas_total", "integracao", "webhook").count());
    }

    @Test
    void registraFalhaDoPostgresEDeProcessamentoDeOs() {
        var request = new MockHttpServletRequest("POST", "/api/ordens-servico");
        double dbBefore = Metrics.counter("oficina_integracao_falhas_total", "integracao", "postgres").count();
        double osBefore = Metrics.counter("oficina_os_falhas_total", "tipo", "DataAccessResourceFailureException").count();
        var response = handler.handleGeneric(new DataAccessResourceFailureException("simulado"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(dbBefore + 1, Metrics.counter("oficina_integracao_falhas_total", "integracao", "postgres").count());
        assertEquals(osBefore + 1, Metrics.counter("oficina_os_falhas_total", "tipo", "DataAccessResourceFailureException").count());
    }
}

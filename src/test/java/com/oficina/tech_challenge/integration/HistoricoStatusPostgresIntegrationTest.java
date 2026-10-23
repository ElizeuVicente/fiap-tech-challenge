package com.oficina.tech_challenge.integration;

import com.oficina.tech_challenge.application.dtos.AberturaOrdemServicoCommand;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.domain.entities.StatusOrdemServico;
import com.oficina.tech_challenge.domain.repositories.OrdemServicoRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.flyway.schemas=oficina_test_history",
        "spring.jpa.properties.hibernate.default_schema=oficina_test_history",
        "spring.jpa.hibernate.ddl-auto=validate",
        "management.tracing.sampling.probability=0",
        "security.jwt.secret=0123456789abcdef0123456789abcdef",
        "external.webhook.secret=test-history-webhook"
})
@Testcontainers(disabledWithoutDocker = true)
class HistoricoStatusPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("oficina").withUsername("oficina").withPassword("oficina");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?")
                        + "currentSchema=oficina_test_history");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private IGerenciadorOrdemServico ordens;
    @Autowired private OrdemServicoRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @Transactional
    void persistsOpeningAndTransitionHistoryWithMandatoryOrderForeignKey() {
        var command = new AberturaOrdemServicoCommand(
                new AberturaOrdemServicoCommand.ClienteData(
                        "Cliente Integracao", "52998224725", "cliente@example.com", "11999999999"),
                new AberturaOrdemServicoCommand.VeiculoData("FIA3A23", "Fiat", "Argo", 2022),
                List.of(), List.of());
        var order = ordens.abrirOrdemServico(command);

        entityManager.flush();
        entityManager.clear();
        assertEquals(1, countHistory(order.getId()));
        assertEquals(StatusOrdemServico.RECEBIDA,
                repository.findById(order.getId()).orElseThrow().getHistoricoStatus().get(0).getNovoStatus());

        ordens.registrarDiagnostico(order.getId(), "Diagnostico sintetico");
        entityManager.flush();
        entityManager.clear();
        assertEquals(2, countHistory(order.getId()));
    }

    private Integer countHistory(java.util.UUID orderId) {
        return jdbc.queryForObject(
                "select count(*) from oficina_test_history.historico_status_ordem_servico where ordem_servico_id = ?",
                Integer.class, orderId);
    }
}

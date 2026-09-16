package com.oficina.tech_challenge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("oficina").withUsername("oficina").withPassword("oficina");

    @Test
    void appliesAllMigrationsOnPostgres() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.prepareStatement("select count(*) from information_schema.tables where table_name = 'historico_status_ordem_servico'")) {
            var result = statement.executeQuery(); result.next();
            assertEquals(1, result.getInt(1));
        }
    }
}

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

    @Test
    void migratesLegacyItemsWithoutChangingTheirFrozenPrices() throws Exception {
        String schema = "catalog_migration_test";
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).schemas(schema).target("17").load().migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        var part = java.util.UUID.randomUUID(); var service = java.util.UUID.randomUUID();
        var partItem = java.util.UUID.randomUUID(); var serviceItem = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_migration_test.peca(id,nome,preco,quantidade_estoque) VALUES (?,?,?,?)", part, "Filtro", 99, 5);
        jdbc.update("INSERT INTO catalog_migration_test.servico(id,nome,preco_base) VALUES (?,?,?)", service, "Reparo", 200);
        jdbc.update("INSERT INTO catalog_migration_test.item_peca(id,peca_id,quantidade,preco_aplicado) VALUES (?,?,?,?)", partItem, part, 2, 15);
        jdbc.update("INSERT INTO catalog_migration_test.item_servico(id,servico_id,preco_aplicado) VALUES (?,?,?)", serviceItem, service, 50);
        Flyway.configure().dataSource(source).schemas(schema).load().migrate();
        var savedPart = jdbc.queryForMap("SELECT referencia_externa_id,descricao_snapshot,preco_aplicado FROM catalog_migration_test.item_peca WHERE id=?", partItem);
        var savedService = jdbc.queryForMap("SELECT referencia_externa_id,descricao_snapshot,preco_aplicado FROM catalog_migration_test.item_servico WHERE id=?", serviceItem);
        assertEquals(part, savedPart.get("referencia_externa_id"));
        assertEquals("Filtro", savedPart.get("descricao_snapshot"));
        assertEquals(0, new java.math.BigDecimal("15.00").compareTo((java.math.BigDecimal) savedPart.get("preco_aplicado")));
        assertEquals(service, savedService.get("referencia_externa_id"));
        assertEquals("Reparo", savedService.get("descricao_snapshot"));
        assertEquals(0, new java.math.BigDecimal("50.00").compareTo((java.math.BigDecimal) savedService.get("preco_aplicado")));
    }

    @Test
    void legacyBranchAuditDistinguishesAgreementConflictAndMissingEvidence() throws Exception {
        String schema = "branch_reconciliation_test";
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).schemas(schema).load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET search_path TO branch_reconciliation_test");
            var agreed = java.util.UUID.randomUUID(); var conflict = java.util.UUID.randomUUID(); var missing = java.util.UUID.randomUUID();
            var identifiedConflict = java.util.UUID.randomUUID();
            var branch = java.util.UUID.randomUUID(); var other = java.util.UUID.randomUUID();
            for (var id : java.util.List.of(agreed, conflict, missing, identifiedConflict))
                statement.execute("INSERT INTO ordem_servico(id) VALUES ('" + id + "')");
            statement.execute("UPDATE ordem_servico SET filial_id='" + branch + "' WHERE id='" + identifiedConflict + "'");
            statement.execute("INSERT INTO execution_outbox(order_id,branch_id) VALUES ('" + identifiedConflict + "','" + other + "')");
            for (var id : java.util.List.of(agreed, conflict)) {
                statement.execute("INSERT INTO execution_outbox(order_id,branch_id) VALUES ('" + id + "','" + branch + "')");
                var billingBranch = id.equals(agreed) ? branch : other;
                statement.execute("INSERT INTO billing_quote_requests(order_id,branch_id,saga_id,correlation_id,amount) VALUES ('" + id + "','" + billingBranch + "','" + java.util.UUID.randomUUID() + "','" + java.util.UUID.randomUUID() + "',1)");
            }
            String sql = java.nio.file.Files.readString(java.nio.file.Path.of("scripts/auditar-filiais-legadas.sql"));
            var result = statement.executeQuery(sql);
            var statuses = new java.util.HashMap<java.util.UUID, String>();
            while (result.next()) statuses.put(result.getObject("ordem_id", java.util.UUID.class), result.getString("situacao"));
            assertEquals("CANDIDATA_CONCORDANTE", statuses.get(agreed));
            assertEquals("CONFLITO", statuses.get(conflict));
            assertEquals("EVIDENCIA_INSUFICIENTE", statuses.get(missing));
            assertEquals("CONFLITO_COM_FILIAL_ATUAL", statuses.get(identifiedConflict));
        }
    }

    @Test
    void branchGrantsAndRevocationsAreAuditedAndUpdatesAreBlocked() throws Exception {
        String schema = "branch_audit_test";
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).schemas(schema).load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET search_path TO branch_audit_test");
            statement.execute("SELECT set_config('oficina.ator_declarado','operador-teste',false)");
            statement.execute("SELECT set_config('oficina.motivo_vinculo','teste controlado',false)");
            statement.execute("INSERT INTO usuario(id,username) VALUES ('11111111-1111-1111-1111-111111111111','admin-teste')");
            statement.execute("INSERT INTO usuario_filial(username,filial_id) VALUES ('admin-teste','22222222-2222-2222-2222-222222222222')");
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> statement.execute("UPDATE usuario_filial SET filial_id='33333333-3333-3333-3333-333333333333'"));
            statement.execute("DELETE FROM usuario_filial WHERE username='admin-teste'");
            var result = statement.executeQuery("SELECT operacao, ator_banco, ator_declarado, motivo FROM usuario_filial_auditoria ORDER BY id");
            result.next(); assertEquals("INSERT", result.getString(1)); assertEquals("oficina", result.getString(2));
            assertEquals("operador-teste", result.getString(3)); assertEquals("teste controlado", result.getString(4));
            result.next(); assertEquals("DELETE", result.getString(1)); assertEquals("oficina", result.getString(2));
            assertEquals("operador-teste", result.getString(3)); assertEquals("teste controlado", result.getString(4));
            assertEquals(false, result.next());
        }
    }

    @Test
    void isolatesHomologAndProductionSchemas() throws Exception {
        for (String schema : java.util.List.of("oficina_homolog", "oficina_prod")) {
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(schema).load().migrate();
        }
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.prepareStatement("""
                     select count(*) from information_schema.tables
                     where table_schema in ('oficina_homolog', 'oficina_prod')
                       and table_name = 'historico_status_ordem_servico'
                     """)) {
            var result = statement.executeQuery();
            result.next();
            assertEquals(2, result.getInt(1));
        }
    }
}

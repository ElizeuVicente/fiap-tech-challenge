package com.oficina.tech_challenge.infrastructure.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Séries de baixa cardinalidade; a consulta agrega no PostgreSQL, sem carregar todas as OS em memória. */
@Component
public class BusinessMetrics {
    private final JdbcTemplate jdbc;
    private final AtomicReference<Double> volumeDiario = new AtomicReference<>(0.0);
    private final Map<String, AtomicReference<Double>> etapaMinutos = Map.of(
            "DIAGNOSTICO", new AtomicReference<>(0.0),
            "EXECUCAO", new AtomicReference<>(0.0),
            "FINALIZADA", new AtomicReference<>(0.0));

    public BusinessMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("oficina_ordens_servico_volume_diario", volumeDiario, AtomicReference::get)
                .description("Ordens abertas no dia corrente").register(registry);
        etapaMinutos.forEach((etapa, valor) -> Gauge.builder("oficina_os_duracao_etapa_minutos", valor, AtomicReference::get)
                .tag("etapa", etapa).description("Duração média dos intervalos encerrados na etapa")
                .register(registry));
    }

    @Scheduled(initialDelay = 20_000, fixedDelay = 60_000)
    public void refresh() {
        Long volume = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ordem_servico
                WHERE data_criacao >= CURRENT_DATE AND data_criacao < CURRENT_DATE + INTERVAL '1 day'
                """, Long.class);
        volumeDiario.set(volume == null ? 0.0 : volume.doubleValue());
        etapaMinutos.values().forEach(value -> value.set(0.0));
        jdbc.query("""
                WITH intervalos AS (
                  SELECT novo_status, EXTRACT(EPOCH FROM
                    (LEAD(data_hora) OVER (PARTITION BY ordem_servico_id ORDER BY data_hora) - data_hora)) / 60 AS minutos
                  FROM historico_status_ordem_servico
                )
                SELECT novo_status, AVG(minutos) FROM intervalos
                WHERE minutos IS NOT NULL AND novo_status IN ('DIAGNOSTICO','EXECUCAO','FINALIZADA')
                GROUP BY novo_status
                """, rs -> {
            var metric = etapaMinutos.get(rs.getString(1));
            if (metric != null) metric.set(rs.getDouble(2));
        });
    }
}

package com.oficina.tech_challenge.application.dtos;

import java.time.LocalDate;
import java.util.Map;

public record MetricasNegocioData(LocalDate dataReferencia, long volumeDiario,
        Map<String, Double> tempoMedioMinutosPorStatus) { }

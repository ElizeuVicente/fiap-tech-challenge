package com.oficina.tech_challenge.application.interfaces;

import com.oficina.tech_challenge.domain.entities.Servico;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IGerenciadorServico {
    Servico cadastrarServico(String nome, BigDecimal precoBase, Integer tempoEstimado);
    Servico atualizarServico(UUID id, String nome, BigDecimal precoBase, Integer tempoEstimado);
    void removerServico(UUID id);
    Optional<Servico> buscarPorId(UUID id);
    List<Servico> listarServicos();
}

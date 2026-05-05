package com.oficina.tech_challenge.application.interfaces;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.domain.entities.OrdemServico;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IGerenciadorOrdemServico {
    OrdemServico criarOS(UUID clienteId, String placa);
    void adicionarItens(UUID osId, List<UUID> servicoIds, List<UUID> pecaIds, List<Integer> quantidades);
    OrdemServico registrarDiagnostico(UUID osId, String diagnostico);
    OrdemServico gerarOrcamento(UUID osId);
    void aprovarOrcamento(UUID osId);
    void finalizarOS(UUID osId);
    void entregarOS(UUID osId);
    Optional<OrdemServico> buscarPorId(UUID id);
    List<OrdemServico> buscarPorCpfCnpj(String cpfCnpj);
    List<OrdemServico> listarTodas();
    MonitoramentoData getMonitoramento();
}

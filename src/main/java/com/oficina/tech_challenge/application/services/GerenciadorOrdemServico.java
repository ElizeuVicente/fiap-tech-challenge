package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.domain.entities.*;
import com.oficina.tech_challenge.domain.repositories.OrdemServicoRepository;
import com.oficina.tech_challenge.domain.repositories.PecaRepository;
import com.oficina.tech_challenge.domain.repositories.ServicoRepository;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GerenciadorOrdemServico implements IGerenciadorOrdemServico {
    private final OrdemServicoRepository osRepository;
    private final ServicoRepository servicoRepository;
    private final PecaRepository pecaRepository;
    private final GerenciadorCliente gerenciadorCliente;

    public GerenciadorOrdemServico(OrdemServicoRepository osRepository,
            ServicoRepository servicoRepository,
            PecaRepository pecaRepository,
            GerenciadorCliente gerenciadorCliente) {
        this.osRepository = osRepository;
        this.servicoRepository = servicoRepository;
        this.pecaRepository = pecaRepository;
        this.gerenciadorCliente = gerenciadorCliente;
    }

    @Transactional
    public OrdemServico criarOS(UUID clienteId, String placa) {
        Cliente cliente = gerenciadorCliente.buscarPorId(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));

        Veiculo veiculo = cliente.getVeiculos().stream()
                .filter(v -> v.getPlaca().equals(placa))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado para este cliente"));

        OrdemServico os = new OrdemServico(cliente, veiculo);
        return osRepository.save(os);
    }

    @Transactional
    public void adicionarItens(UUID osId, List<UUID> servicoIds, List<UUID> pecaIds, List<Integer> quantidades) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));

        for (UUID sId : servicoIds) {
            Servico s = servicoRepository.findById(sId)
                    .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado: " + sId));
            os.adicionarServico(s);
        }

        for (int i = 0; i < pecaIds.size(); i++) {
            UUID pId = pecaIds.get(i);
            int qtd = quantidades.get(i);
            Peca p = pecaRepository.findById(pId)
                    .orElseThrow(() -> new IllegalArgumentException("Peça não encontrada: " + pId));
            os.adicionarPeca(p, qtd);
        }

        osRepository.save(os);
    }

    @Transactional
    public OrdemServico registrarDiagnostico(UUID osId, String diagnostico) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        os.registrarDiagnostico(diagnostico);
        return osRepository.save(os);
    }

    @Transactional
    public OrdemServico gerarOrcamento(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        os.gerarOrcamento();
        return osRepository.save(os);
    }

    @Transactional
    public void aprovarOrcamento(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        for (ItemPeca item : os.getPecas()) {
            item.getPeca().baixarEstoque(item.getQuantidade());
        }
        os.aprovar();
        osRepository.save(os);
    }

    @Transactional
    public void finalizarOS(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        System.out.println("Tentando finalizar OS: " + osId + " | Status atual: " + os.getStatus());
        os.finalizar();
        osRepository.save(os);
    }

    @Transactional
    public void entregarOS(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        os.entregar();
        osRepository.save(os);
    }

    public Optional<OrdemServico> buscarPorId(UUID id) {
        return osRepository.findById(id);
    }

    public List<OrdemServico> buscarPorCpfCnpj(String cpfCnpj) {
        return osRepository.findByClienteCpfCnpj(new CpfCnpj(cpfCnpj));
    }

    public List<OrdemServico> listarTodas() {
        return osRepository.findAll();
    }

    public MonitoramentoData getMonitoramento() {
        List<OrdemServico> todas = osRepository.findAll();
        long totalFinalizadas = todas.stream()
                .filter(os -> os.getDataFinalizacao() != null && os.getDataInicioExecucao() != null)
                .count();

        if (totalFinalizadas == 0) {
            return new MonitoramentoData(0.0, 0);
        }

        double tempoMedio = todas.stream()
                .filter(os -> os.getDataFinalizacao() != null && os.getDataInicioExecucao() != null)
                .mapToLong(os -> java.time.Duration.between(os.getDataInicioExecucao(), os.getDataFinalizacao())
                        .toMinutes())
                .average()
                .orElse(0.0);

        return new MonitoramentoData(tempoMedio, (int) totalFinalizadas);
    }
}

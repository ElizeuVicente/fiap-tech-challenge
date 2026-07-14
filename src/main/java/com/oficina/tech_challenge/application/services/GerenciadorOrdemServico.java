package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.application.dtos.AberturaOrdemServicoCommand;
import com.oficina.tech_challenge.application.dtos.AtualizacaoStatusCommand;
import com.oficina.tech_challenge.application.dtos.NotificacaoOrcamentoCommand;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.domain.entities.*;
import com.oficina.tech_challenge.domain.repositories.ClienteRepository;
import com.oficina.tech_challenge.domain.repositories.NotificacaoOrcamentoRepository;
import com.oficina.tech_challenge.domain.repositories.OrdemServicoRepository;
import com.oficina.tech_challenge.domain.repositories.PecaRepository;
import com.oficina.tech_challenge.domain.repositories.ServicoRepository;
import com.oficina.tech_challenge.domain.repositories.VeiculoRepository;
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
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;
    private final NotificacaoOrcamentoRepository notificacaoRepository;
    private final GerenciadorCliente gerenciadorCliente;

    public GerenciadorOrdemServico(OrdemServicoRepository osRepository,
            ServicoRepository servicoRepository,
            PecaRepository pecaRepository,
            ClienteRepository clienteRepository,
            VeiculoRepository veiculoRepository,
            NotificacaoOrcamentoRepository notificacaoRepository,
            GerenciadorCliente gerenciadorCliente) {
        this.osRepository = osRepository;
        this.servicoRepository = servicoRepository;
        this.pecaRepository = pecaRepository;
        this.clienteRepository = clienteRepository;
        this.veiculoRepository = veiculoRepository;
        this.notificacaoRepository = notificacaoRepository;
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
    public OrdemServico abrirOrdemServico(AberturaOrdemServicoCommand command) {
        validarAbertura(command);

        Cliente cliente = clienteRepository.findByCpfCnpj(new CpfCnpj(command.cliente().cpfCnpj()))
                .orElseGet(() -> new Cliente(
                        command.cliente().nome(),
                        new CpfCnpj(command.cliente().cpfCnpj()),
                        emailOuPadrao(command.cliente().email()),
                        command.cliente().telefone()));

        String placaNormalizada = command.veiculo().placa().trim().toUpperCase().replace("-", "");
        cliente.getVeiculos().stream()
                .filter(v -> v.getPlaca().equals(placaNormalizada))
                .findFirst()
                .orElseGet(() -> {
                    Veiculo novoVeiculo = new Veiculo(
                            command.veiculo().placa(),
                            command.veiculo().marca(),
                            command.veiculo().modelo(),
                            command.veiculo().ano());
                    Veiculo veiculoPersistido = veiculoRepository.saveAndFlush(novoVeiculo);
                    cliente.adicionarVeiculo(veiculoPersistido);
                    return veiculoPersistido;
                });

        Cliente clientePersistido = clienteRepository.saveAndFlush(cliente);
        Veiculo veiculoPersistido = clientePersistido.getVeiculos().stream()
                .filter(v -> v.getPlaca().equals(placaNormalizada))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Veículo não encontrado após persistência"));
        OrdemServico os = new OrdemServico(clientePersistido, veiculoPersistido);
        adicionarItensNaOrdem(os, command.servicos(), command.pecas());
        return osRepository.save(os);
    }

    @Transactional
    public void adicionarItens(UUID osId, List<UUID> servicoIds, List<UUID> pecaIds, List<Integer> quantidades) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));

        if (servicoIds == null) {
            servicoIds = List.of();
        }
        if (pecaIds == null) {
            pecaIds = List.of();
        }
        if (quantidades == null) {
            quantidades = List.of();
        }
        if (pecaIds.size() != quantidades.size()) {
            throw new IllegalArgumentException("A quantidade de peças deve corresponder à lista de quantidades");
        }

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
        aprovarComBaixaDeEstoque(os);
        osRepository.save(os);
    }

    @Transactional
    public OrdemServico processarNotificacaoOrcamento(UUID osId, NotificacaoOrcamentoCommand command) {
        validarNotificacao(command);
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));

        Optional<NotificacaoOrcamento> notificacaoExistente =
                notificacaoRepository.findByIdentificadorExterno(command.identificadorExterno());
        if (notificacaoExistente.isPresent()) {
            UUID ordemNotificadaId = notificacaoExistente.get().getOrdemServico().getId();
            if (ordemNotificadaId != null && !ordemNotificadaId.equals(osId)) {
                throw new IllegalArgumentException("Identificador externo já processado para outra OS");
            }
            return notificacaoExistente.get().getOrdemServico();
        }

        if (command.decisao() == DecisaoOrcamento.APROVADO) {
            aprovarComBaixaDeEstoque(os);
        } else {
            os.recusar();
        }

        OrdemServico osAtualizada = osRepository.save(os);
        notificacaoRepository.save(new NotificacaoOrcamento(
                osAtualizada,
                command.decisao(),
                command.origem(),
                command.dataHora(),
                command.identificadorExterno()));
        return osAtualizada;
    }

    @Transactional
    public OrdemServico atualizarStatus(UUID osId, AtualizacaoStatusCommand command) {
        if (command == null || command.novoStatus() == null) {
            throw new IllegalArgumentException("Novo status é obrigatório");
        }
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));

        if (command.novoStatus() == StatusOrdemServico.EXECUCAO) {
            aprovarComBaixaDeEstoque(os);
        } else {
            os.atualizarStatus(command.novoStatus());
        }
        return osRepository.save(os);
    }

    @Transactional
    public void finalizarOS(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
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

    public List<OrdemServico> listarOperacionais() {
        return osRepository.findOperacionaisOrdenadas();
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

    private void adicionarItensNaOrdem(OrdemServico os, List<AberturaOrdemServicoCommand.ServicoData> servicos,
            List<AberturaOrdemServicoCommand.PecaData> pecas) {
        if (servicos != null) {
            for (AberturaOrdemServicoCommand.ServicoData item : servicos) {
                Servico servico = servicoRepository.findById(item.servicoId())
                        .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado: " + item.servicoId()));
                os.adicionarServico(servico);
            }
        }
        if (pecas != null) {
            for (AberturaOrdemServicoCommand.PecaData item : pecas) {
                Peca peca = pecaRepository.findById(item.pecaId())
                        .orElseThrow(() -> new IllegalArgumentException("Peça não encontrada: " + item.pecaId()));
                os.adicionarPeca(peca, item.quantidade());
            }
        }
    }

    private void aprovarComBaixaDeEstoque(OrdemServico os) {
        if (os.getStatus() != StatusOrdemServico.AGUARDANDO_APROVACAO) {
            throw new IllegalStateException("Somente OS aguardando aprovação podem ser aprovadas");
        }
        for (ItemPeca item : os.getPecas()) {
            item.getPeca().baixarEstoque(item.getQuantidade());
        }
        os.aprovar();
    }

    private void validarAbertura(AberturaOrdemServicoCommand command) {
        if (command == null || command.cliente() == null || command.veiculo() == null) {
            throw new IllegalArgumentException("Cliente e veículo são obrigatórios para abertura da OS");
        }
    }

    private void validarNotificacao(NotificacaoOrcamentoCommand command) {
        if (command == null || command.decisao() == null) {
            throw new IllegalArgumentException("Decisão da notificação é obrigatória");
        }
        if (command.identificadorExterno() == null || command.identificadorExterno().isBlank()) {
            throw new IllegalArgumentException("Identificador externo é obrigatório");
        }
    }

    private String emailOuPadrao(String email) {
        if (email == null || email.isBlank()) {
            return "cliente.sem.email@oficina.local";
        }
        return email;
    }
}

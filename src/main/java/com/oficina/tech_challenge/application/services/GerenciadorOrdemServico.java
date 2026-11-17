package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.application.dtos.MetricasNegocioData;
import com.oficina.tech_challenge.application.dtos.AberturaOrdemServicoCommand;
import com.oficina.tech_challenge.application.dtos.AtualizacaoStatusCommand;
import com.oficina.tech_challenge.application.dtos.NotificacaoOrcamentoCommand;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.application.interfaces.ExecutionOrderDispatch;
import com.oficina.tech_challenge.application.interfaces.BillingQuoteDispatch;
import com.oficina.tech_challenge.application.interfaces.ExecutionCatalogQuery;
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
import org.slf4j.MDC;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Service
public class GerenciadorOrdemServico implements IGerenciadorOrdemServico {
    private final OrdemServicoRepository osRepository;
    private final ServicoRepository servicoRepository;
    private final PecaRepository pecaRepository;
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;
    private final NotificacaoOrcamentoRepository notificacaoRepository;
    private final GerenciadorCliente gerenciadorCliente;
    private ExecutionOrderDispatch executionDispatch;
    private BillingQuoteDispatch billingDispatch;
    private ExecutionCatalogQuery executionCatalog;
    @org.springframework.beans.factory.annotation.Value("${oficina.execution.branch-id:}")
    private String catalogBranchId;
    @org.springframework.beans.factory.annotation.Value("${oficina.fase4.payment-gate:false}")
    private boolean paymentGate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setExecutionDispatch(ExecutionOrderDispatch executionDispatch) { this.executionDispatch = executionDispatch; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setBillingDispatch(BillingQuoteDispatch billingDispatch) { this.billingDispatch = billingDispatch; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setExecutionCatalog(ExecutionCatalogQuery executionCatalog) { this.executionCatalog = executionCatalog; }

    private OrdemServico registrarAbertura(OrdemServico os) {
        OrdemServico saved = osRepository.save(os);
        if (executionDispatch != null) {
            if (saved.getFilialId() == null) executionDispatch.schedule(saved.getId());
            else executionDispatch.schedule(saved.getId(), saved.getFilialId());
        }
        return saved;
    }

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
        return criarOS(clienteId, placa, null);
    }

    @Transactional
    public OrdemServico criarOS(UUID clienteId, String placa, UUID filialId) {
        Cliente cliente = gerenciadorCliente.buscarPorId(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));

        Veiculo veiculo = cliente.getVeiculos().stream()
                .filter(v -> v.getPlaca().equals(placa))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado para este cliente"));

        OrdemServico os = new OrdemServico(cliente, veiculo);
        if (filialId != null) os.identificarFilial(filialId);
        return registrarAbertura(os);
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
        if (command.filialId() != null) os.identificarFilial(command.filialId());
        adicionarItensNaOrdem(os, command.servicos(), command.pecas());
        return registrarAbertura(os);
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
            adicionarServicoPorId(os, sId);
        }

        for (int i = 0; i < pecaIds.size(); i++) {
            UUID pId = pecaIds.get(i);
            int qtd = quantidades.get(i);
            adicionarPecaPorId(os, pId, qtd);
        }

        osRepository.save(os);
    }

    @Transactional
    public OrdemServico registrarDiagnostico(UUID osId, String diagnostico) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        StatusOrdemServico anterior = os.getStatus(); os.registrarDiagnostico(diagnostico); os.registrarHistorico(anterior, "API", MDC.get("correlationId"));
        return osRepository.save(os);
    }

    /** Applies an external diagnosis once without overwriting a later local transition. */
    @Transactional
    public void registrarDiagnosticoExecucao(UUID osId, String diagnostico) {
        if (diagnostico == null || diagnostico.isBlank() || diagnostico.length() > 4000)
            throw new IllegalArgumentException("Diagnóstico inválido");
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        if (os.getStatus() == StatusOrdemServico.DIAGNOSTICO && diagnostico.equals(os.getDiagnostico())) return;
        if (os.getStatus() != StatusOrdemServico.RECEBIDA)
            throw new IllegalStateException("Diagnóstico externo conflita com o estado atual da OS");
        StatusOrdemServico anterior = os.getStatus();
        os.registrarDiagnostico(diagnostico);
        os.registrarHistorico(anterior, "EXECUCAO", null);
        osRepository.save(os);
    }

    @Transactional
    public OrdemServico gerarOrcamento(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        if (billingDispatch != null && os.getValorTotal().signum() <= 0) throw new IllegalArgumentException("Orçamento deve ter valor positivo");
        StatusOrdemServico anterior = os.getStatus(); os.gerarOrcamento(); os.registrarHistorico(anterior, "API", MDC.get("correlationId"));
        if (billingDispatch != null) {
            var items = new java.util.ArrayList<BillingQuoteDispatch.Item>();
            os.getServicos().forEach(item -> items.add(new BillingQuoteDispatch.Item("SERVICE", item.getReferenciaCatalogoId(), 1, item.getPrecoAplicado())));
            os.getPecas().forEach(item -> items.add(new BillingQuoteDispatch.Item("PART", item.getReferenciaCatalogoId(), item.getQuantidade(), item.getPrecoAplicado())));
            if (items.isEmpty() || items.size() > 100) throw new IllegalArgumentException("Orçamento deve ter de 1 a 100 itens");
            if (os.getFilialId() == null) billingDispatch.schedule(osId, os.getValorTotal(), items);
            else billingDispatch.schedule(osId, os.getFilialId(), os.getValorTotal(), items);
        }
        return osRepository.save(os);
    }

    @Transactional
    public void aprovarOrcamento(UUID osId) {
        if (billingDispatch != null) throw new IllegalStateException("Decisão deve ser informada pelo serviço de orçamento");
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        StatusOrdemServico anterior = os.getStatus(); aprovarConformeModo(os); os.registrarHistorico(anterior, "API", MDC.get("correlationId"));
        osRepository.save(os);
    }

    @Transactional
    public OrdemServico processarNotificacaoOrcamento(UUID osId, NotificacaoOrcamentoCommand command) {
        if (billingDispatch != null) throw new IllegalStateException("Decisão deve ser informada pelo serviço de orçamento");
        return aplicarDecisaoOrcamento(osId, command);
    }

    @Transactional
    public OrdemServico processarDecisaoBilling(UUID osId, NotificacaoOrcamentoCommand command) {
        return aplicarDecisaoOrcamento(osId, command);
    }

    private OrdemServico aplicarDecisaoOrcamento(UUID osId, NotificacaoOrcamentoCommand command) {
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

        StatusOrdemServico anterior = os.getStatus();
        if (command.decisao() == DecisaoOrcamento.APROVADO) {
            aprovarConformeModo(os);
        } else {
            os.recusar();
        }

        os.registrarHistorico(anterior, command.origem(), MDC.get("correlationId"));
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
        if (executionDispatch != null && command.novoStatus() == StatusOrdemServico.FINALIZADA)
            throw new IllegalStateException("Conclusão deve ser informada pelo serviço de execução");
        if (billingDispatch != null && command.novoStatus() == StatusOrdemServico.RECUSADA)
            throw new IllegalStateException("Decisão deve ser informada pelo serviço de orçamento");
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));

        StatusOrdemServico anterior = os.getStatus();
        if (command.novoStatus() == StatusOrdemServico.EXECUCAO) {
            if (paymentGate) throw new IllegalStateException("Execução requer pagamento e reserva confirmados pela integração");
            aprovarComBaixaDeEstoque(os);
        } else {
            os.atualizarStatus(command.novoStatus());
        }
        os.registrarHistorico(anterior, command.origem(), MDC.get("correlationId"));
        return osRepository.save(os);
    }

    @Transactional
    public void finalizarOS(UUID osId) {
        if (executionDispatch != null) throw new IllegalStateException("Conclusão deve ser informada pelo serviço de execução");
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        StatusOrdemServico anterior = os.getStatus(); os.finalizar(); os.registrarHistorico(anterior, "API", MDC.get("correlationId"));
        osRepository.save(os);
    }

    @Transactional
    public void registrarConclusaoExecucao(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        if (os.getStatus() == StatusOrdemServico.FINALIZADA || os.getStatus() == StatusOrdemServico.ENTREGUE) return;
        StatusOrdemServico anterior = os.getStatus();
        os.finalizar();
        os.registrarHistorico(anterior, "EXECUCAO", null);
        osRepository.save(os);
    }

    @Transactional
    public void registrarInicioExecucaoConfirmada(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        if (os.getStatus() == StatusOrdemServico.EXECUCAO || os.getStatus() == StatusOrdemServico.FINALIZADA || os.getStatus() == StatusOrdemServico.ENTREGUE) return;
        StatusOrdemServico anterior = os.getStatus();
        os.iniciarExecucaoConfirmada();
        os.registrarHistorico(anterior, "EXECUCAO", null);
        osRepository.save(os);
    }

    @Transactional
    public void entregarOS(UUID osId) {
        OrdemServico os = osRepository.findById(osId)
                .orElseThrow(() -> new IllegalArgumentException("OS não encontrada"));
        StatusOrdemServico anterior = os.getStatus(); os.entregar(); os.registrarHistorico(anterior, "API", MDC.get("correlationId"));
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

    public List<OrdemServico> listarPorFiliais(List<UUID> filiais, boolean operacional) {
        if (filiais == null || filiais.isEmpty()) return List.of();
        return operacional ? osRepository.findOperacionaisPorFiliais(filiais) : osRepository.findByFilialIdIn(filiais);
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

    @Transactional(readOnly = true)
    public MetricasNegocioData getMetricasNegocio() {
        LocalDate hoje = LocalDate.now();
        List<OrdemServico> ordens = osRepository.findAll();
        long volume = ordens.stream().filter(o -> o.getDataCriacao().toLocalDate().equals(hoje)).count();
        Map<StatusOrdemServico, Long> minutos = new EnumMap<>(StatusOrdemServico.class);
        Map<StatusOrdemServico, Long> ocorrencias = new EnumMap<>(StatusOrdemServico.class);
        for (OrdemServico os : ordens) {
            List<HistoricoStatusOrdemServico> historico = os.getHistoricoStatus().stream()
                    .sorted(java.util.Comparator.comparing(HistoricoStatusOrdemServico::getDataHora)).toList();
            for (int i = 0; i < historico.size(); i++) {
                HistoricoStatusOrdemServico atual = historico.get(i);
                LocalDateTime fim = i + 1 < historico.size() ? historico.get(i + 1).getDataHora() : LocalDateTime.now();
                if (atual.getNovoStatus() == StatusOrdemServico.DIAGNOSTICO || atual.getNovoStatus() == StatusOrdemServico.EXECUCAO || atual.getNovoStatus() == StatusOrdemServico.FINALIZADA) {
                    minutos.merge(atual.getNovoStatus(), Duration.between(atual.getDataHora(), fim).toMinutes(), Long::sum);
                    ocorrencias.merge(atual.getNovoStatus(), 1L, Long::sum);
                }
            }
        }
        Map<String, Double> medias = new java.util.LinkedHashMap<>();
        for (StatusOrdemServico status : List.of(StatusOrdemServico.DIAGNOSTICO, StatusOrdemServico.EXECUCAO, StatusOrdemServico.FINALIZADA)) {
            medias.put(status.name(), ocorrencias.containsKey(status) ? (double) minutos.get(status) / ocorrencias.get(status) : 0.0);
        }
        return new MetricasNegocioData(hoje, volume, medias);
    }

    private void adicionarItensNaOrdem(OrdemServico os, List<AberturaOrdemServicoCommand.ServicoData> servicos,
            List<AberturaOrdemServicoCommand.PecaData> pecas) {
        if (servicos != null) {
            for (AberturaOrdemServicoCommand.ServicoData item : servicos) {
                adicionarServicoPorId(os, item.servicoId());
            }
        }
        if (pecas != null) {
            for (AberturaOrdemServicoCommand.PecaData item : pecas) {
                adicionarPecaPorId(os, item.pecaId(), item.quantidade());
            }
        }
    }

    private UUID catalogBranch(OrdemServico os) {
        if (os.getFilialId() != null) return os.getFilialId();
        try { return UUID.fromString(catalogBranchId); }
        catch (RuntimeException invalid) { throw new IllegalStateException("Filial do catálogo não configurada", invalid); }
    }

    private void adicionarServicoPorId(OrdemServico os, UUID id) {
        if (executionCatalog == null) {
            os.adicionarServico(servicoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado: " + id)));
            return;
        }
        if (!paymentGate) throw new IllegalStateException("Catálogo externo exige o controle de pagamento da Fase 4");
        var item = executionCatalog.service(catalogBranch(os), id);
        os.adicionarServicoCatalogo(item.referenceId(), item.description(), item.unitPrice());
    }

    private void adicionarPecaPorId(OrdemServico os, UUID id, int quantidade) {
        if (executionCatalog == null) {
            os.adicionarPeca(pecaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Peça não encontrada: " + id)), quantidade);
            return;
        }
        if (!paymentGate) throw new IllegalStateException("Catálogo externo exige o controle de pagamento da Fase 4");
        var item = executionCatalog.part(catalogBranch(os), id);
        os.adicionarPecaCatalogo(item.referenceId(), item.description(), item.unitPrice(), quantidade);
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

    private void aprovarConformeModo(OrdemServico os) {
        if (paymentGate) os.aprovarAguardandoPagamento();
        else aprovarComBaixaDeEstoque(os);
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

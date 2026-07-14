package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.application.dtos.AberturaOrdemServicoCommand;
import com.oficina.tech_challenge.application.dtos.NotificacaoOrcamentoCommand;
import com.oficina.tech_challenge.application.dtos.AtualizacaoStatusCommand;
import com.oficina.tech_challenge.domain.entities.*;
import com.oficina.tech_challenge.domain.repositories.ClienteRepository;
import com.oficina.tech_challenge.domain.repositories.NotificacaoOrcamentoRepository;
import com.oficina.tech_challenge.domain.repositories.OrdemServicoRepository;
import com.oficina.tech_challenge.domain.repositories.PecaRepository;
import com.oficina.tech_challenge.domain.repositories.ServicoRepository;
import com.oficina.tech_challenge.domain.repositories.VeiculoRepository;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GerenciadorOrdemServicoTest {

    @Mock
    private OrdemServicoRepository osRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private PecaRepository pecaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoOrcamentoRepository notificacaoRepository;

    @Mock
    private GerenciadorCliente gerenciadorCliente;

    @InjectMocks
    private GerenciadorOrdemServico gerenciadorOS;

    private Cliente cliente;
    private Veiculo veiculo;
    private OrdemServico os;
    private UUID osId;
    private UUID clienteId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();
        osId = UUID.randomUUID();
        cliente = new Cliente("João Silva", new CpfCnpj("12345678909"), "joao@email.com", "11999999999");
        veiculo = new Veiculo("ABC1234", "Ford", "Fiesta", 2020);
        cliente.adicionarVeiculo(veiculo);
        os = new OrdemServico(cliente, veiculo);
    }

    @Test
    void deveCriarOSComSucesso() {
        when(gerenciadorCliente.buscarPorId(clienteId)).thenReturn(Optional.of(cliente));
        when(osRepository.save(any())).thenReturn(os);

        OrdemServico resultado = gerenciadorOS.criarOS(clienteId, "ABC1234");

        assertNotNull(resultado);
        assertEquals(StatusOrdemServico.RECEBIDA, resultado.getStatus());
        verify(osRepository).save(any(OrdemServico.class));
    }

    @Test
    void deveAbrirOSComClienteVeiculoEItensCompletos() {
        UUID servicoId = UUID.randomUUID();
        UUID pecaId = UUID.randomUUID();
        Servico servico = new Servico("Alinhamento", new BigDecimal("120.00"), 60);
        Peca peca = new Peca("Filtro", new BigDecimal("30.00"), 5);
        AberturaOrdemServicoCommand command = new AberturaOrdemServicoCommand(
                new AberturaOrdemServicoCommand.ClienteData("Cliente Novo", "98765432100", null, "11999999999"),
                new AberturaOrdemServicoCommand.VeiculoData("DEF5G67", "Toyota", "Corolla", 2022),
                List.of(new AberturaOrdemServicoCommand.ServicoData(servicoId)),
                List.of(new AberturaOrdemServicoCommand.PecaData(pecaId, 2)));

        when(clienteRepository.findByCpfCnpj(any())).thenReturn(Optional.empty());
        when(veiculoRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(clienteRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));
        when(osRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrdemServico resultado = gerenciadorOS.abrirOrdemServico(command);

        assertEquals(StatusOrdemServico.RECEBIDA, resultado.getStatus());
        assertEquals(1, resultado.getServicos().size());
        assertEquals(1, resultado.getPecas().size());
        assertEquals("cliente.sem.email@oficina.local", resultado.getCliente().getEmail());
    }

    @Test
    void deveLancarExcecaoAoCriarOSComClienteInexistente() {
        when(gerenciadorCliente.buscarPorId(clienteId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> gerenciadorOS.criarOS(clienteId, "ABC1234"));
    }

    @Test
    void deveLancarExcecaoAoCriarOSComPlacaInexistente() {
        when(gerenciadorCliente.buscarPorId(clienteId)).thenReturn(Optional.of(cliente));

        assertThrows(IllegalArgumentException.class, () -> gerenciadorOS.criarOS(clienteId, "XYZ9999"));
    }

    @Test
    void deveRegistrarDiagnosticoComSucesso() {
        when(osRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);

        OrdemServico resultado = gerenciadorOS.registrarDiagnostico(osId, "Motor com falha");

        assertEquals(StatusOrdemServico.DIAGNOSTICO, resultado.getStatus());
        assertEquals("Motor com falha", resultado.getDiagnostico());
    }

    @Test
    void deveGerarOrcamentoComSucesso() {
        os.registrarDiagnostico("Diagnóstico ok");
        when(osRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);

        OrdemServico resultado = gerenciadorOS.gerarOrcamento(osId);

        assertEquals(StatusOrdemServico.AGUARDANDO_APROVACAO, resultado.getStatus());
    }

    @Test
    void deveAprovarOrcamentoComSucesso() {
        Peca peca = new Peca("Filtro", new BigDecimal("30.00"), 5);
        os.registrarDiagnostico("Ok");
        os.adicionarPeca(peca, 1);
        os.gerarOrcamento();

        when(osRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);

        gerenciadorOS.aprovarOrcamento(osId);

        assertEquals(StatusOrdemServico.EXECUCAO, os.getStatus());
        assertEquals(4, peca.getQuantidadeEstoque()); // baixa feita pelo service antes de aprovar()
    }

    @Test
    void naoDeveBaixarEstoqueAoTentarAprovarOSForaDeAprovacao() {
        Peca peca = new Peca("Filtro", new BigDecimal("30.00"), 5);
        os.registrarDiagnostico("Ok");
        os.adicionarPeca(peca, 1);
        os.gerarOrcamento();
        os.aprovar();

        when(osRepository.findById(osId)).thenReturn(Optional.of(os));

        assertThrows(IllegalStateException.class, () -> gerenciadorOS.aprovarOrcamento(osId));
        assertEquals(5, peca.getQuantidadeEstoque());
    }

    @Test
    void deveProcessarNotificacaoAprovadaComIdempotenciaDeEstoque() {
        Peca peca = new Peca("Filtro", new BigDecimal("30.00"), 5);
        os.registrarDiagnostico("Ok");
        os.adicionarPeca(peca, 1);
        os.gerarOrcamento();
        NotificacaoOrcamentoCommand command = new NotificacaoOrcamentoCommand(
                DecisaoOrcamento.APROVADO,
                "sistema-externo",
                LocalDateTime.now(),
                "ext-123");

        when(osRepository.findById(osId)).thenReturn(Optional.of(os));
        when(notificacaoRepository.findByIdentificadorExterno("ext-123"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new NotificacaoOrcamento(os, DecisaoOrcamento.APROVADO,
                        "sistema-externo", LocalDateTime.now(), "ext-123")));
        when(osRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        gerenciadorOS.processarNotificacaoOrcamento(osId, command);
        OrdemServico repetida = gerenciadorOS.processarNotificacaoOrcamento(osId, command);

        assertEquals(StatusOrdemServico.EXECUCAO, repetida.getStatus());
        assertEquals(4, peca.getQuantidadeEstoque());
        verify(notificacaoRepository, times(1)).save(any(NotificacaoOrcamento.class));
    }

    @Test
    void deveProcessarNotificacaoRecusada() {
        os.registrarDiagnostico("Ok");
        os.gerarOrcamento();
        NotificacaoOrcamentoCommand command = new NotificacaoOrcamentoCommand(
                DecisaoOrcamento.RECUSADO,
                "sistema-externo",
                LocalDateTime.now(),
                "ext-456");

        when(osRepository.findById(osId)).thenReturn(Optional.of(os));
        when(notificacaoRepository.findByIdentificadorExterno("ext-456")).thenReturn(Optional.empty());
        when(osRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrdemServico resultado = gerenciadorOS.processarNotificacaoOrcamento(osId, command);

        assertEquals(StatusOrdemServico.RECUSADA, resultado.getStatus());
        assertNotNull(resultado.getDataFinalizacao());
    }

    @Test
    void deveFinalizarOSComSucesso() {
        Peca peca = new Peca("Filtro", new BigDecimal("30.00"), 5);
        os.registrarDiagnostico("Ok");
        os.adicionarPeca(peca, 1);
        os.gerarOrcamento();
        os.aprovar();

        when(osRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);

        gerenciadorOS.finalizarOS(osId);

        assertEquals(StatusOrdemServico.FINALIZADA, os.getStatus());
        assertNotNull(os.getDataFinalizacao());
    }

    @Test
    void deveEntregarOSComSucesso() {
        Peca peca = new Peca("Filtro", new BigDecimal("30.00"), 5);
        os.registrarDiagnostico("Ok");
        os.adicionarPeca(peca, 1);
        os.gerarOrcamento();
        os.aprovar();
        os.finalizar();

        when(osRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);

        gerenciadorOS.entregarOS(osId);

        assertEquals(StatusOrdemServico.ENTREGUE, os.getStatus());
    }

    @Test
    void deveLancarExcecaoAoEntregarOSNaoFinalizada() {
        when(osRepository.findById(osId)).thenReturn(Optional.of(os));

        assertThrows(IllegalStateException.class, () -> gerenciadorOS.entregarOS(osId));
    }

    @Test
    void deveBuscarOSPorId() {
        when(osRepository.findById(osId)).thenReturn(Optional.of(os));

        Optional<OrdemServico> resultado = gerenciadorOS.buscarPorId(osId);

        assertTrue(resultado.isPresent());
    }

    @Test
    void deveListarTodasAsOS() {
        when(osRepository.findAll()).thenReturn(List.of(os));

        List<OrdemServico> resultado = gerenciadorOS.listarTodas();

        assertEquals(1, resultado.size());
    }

    @Test
    void deveListarOrdensOperacionaisOrdenadasPelaQueryDoRepositorio() {
        when(osRepository.findOperacionaisOrdenadas()).thenReturn(List.of(os));

        List<OrdemServico> resultado = gerenciadorOS.listarOperacionais();

        assertEquals(1, resultado.size());
        verify(osRepository).findOperacionaisOrdenadas();
    }

    @Test
    void deveAtualizarStatusViaFerramentaExterna() {
        when(osRepository.findById(osId)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrdemServico resultado = gerenciadorOS.atualizarStatus(
                osId,
                new AtualizacaoStatusCommand(StatusOrdemServico.DIAGNOSTICO, "webhook-admin"));

        assertEquals(StatusOrdemServico.DIAGNOSTICO, resultado.getStatus());
    }

    @Test
    void deveRetornarMonitoramentoSemOsFinalizadas() {
        when(osRepository.findAll()).thenReturn(List.of(os));

        MonitoramentoData monitoramento = gerenciadorOS.getMonitoramento();

        assertEquals(0.0, monitoramento.tempoMedioExecucaoMinutos());
        assertEquals(0, monitoramento.totalOrdensFinalizadas());
    }

    @Test
    void deveBuscarOSPorCpfCnpj() {
        when(osRepository.findByClienteCpfCnpj(any())).thenReturn(List.of(os));

        List<OrdemServico> resultado = gerenciadorOS.buscarPorCpfCnpj("12345678909");

        assertEquals(1, resultado.size());
    }
}

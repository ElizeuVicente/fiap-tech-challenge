package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.domain.entities.*;
import com.oficina.tech_challenge.domain.repositories.OrdemServicoRepository;
import com.oficina.tech_challenge.domain.repositories.PecaRepository;
import com.oficina.tech_challenge.domain.repositories.ServicoRepository;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
        cliente = new Cliente("João Silva", new CpfCnpj("12345678900"), "joao@email.com", "11999999999");
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
    void deveRetornarMonitoramentoSemOsFinalizadas() {
        when(osRepository.findAll()).thenReturn(List.of(os));

        MonitoramentoData monitoramento = gerenciadorOS.getMonitoramento();

        assertEquals(0.0, monitoramento.tempoMedioExecucaoMinutos());
        assertEquals(0, monitoramento.totalOrdensFinalizadas());
    }

    @Test
    void deveBuscarOSPorCpfCnpj() {
        when(osRepository.findByClienteCpfCnpj(any())).thenReturn(List.of(os));

        List<OrdemServico> resultado = gerenciadorOS.buscarPorCpfCnpj("12345678900");

        assertEquals(1, resultado.size());
    }
}

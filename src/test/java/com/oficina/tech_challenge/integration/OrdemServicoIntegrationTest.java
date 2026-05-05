package com.oficina.tech_challenge.integration;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorCliente;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorEstoque;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorServico;
import com.oficina.tech_challenge.domain.entities.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrdemServicoIntegrationTest {

    @Autowired
    private IGerenciadorCliente gerenciadorCliente;

    @Autowired
    private IGerenciadorEstoque gerenciadorEstoque;

    @Autowired
    private IGerenciadorServico gerenciadorServico;

    @Autowired
    private IGerenciadorOrdemServico gerenciadorOS;

    @Test
    void deveExecutarFluxoCompletoDeOrdemServico() {
        // 1. Cadastrar cliente
        Cliente cliente = gerenciadorCliente.cadastrarCliente(
                "Maria Teste", "98765432100", "maria@email.com", "11988887777");
        assertNotNull(cliente.getId());

        // 2. Adicionar veículo
        gerenciadorCliente.adicionarVeiculo(cliente.getId(), "DEF5G67", "Toyota", "Corolla", 2022);
        Cliente clienteComVeiculo = gerenciadorCliente.buscarPorId(cliente.getId()).orElseThrow();
        assertEquals(1, clienteComVeiculo.getVeiculos().size());

        // 3. Cadastrar serviço e peça
        Servico servico = gerenciadorServico.cadastrarServico("Alinhamento", new BigDecimal("120.00"), 60);
        Peca peca = gerenciadorEstoque.cadastrarPeca("Parafuso M10", new BigDecimal("5.00"), 100);

        // 4. Criar OS
        OrdemServico os = gerenciadorOS.criarOS(cliente.getId(), "DEF5G67");
        assertNotNull(os.getId());
        assertEquals(StatusOrdemServico.RECEBIDA, os.getStatus());

        // 5. Registrar diagnóstico
        OrdemServico osComDiag = gerenciadorOS.registrarDiagnostico(os.getId(), "Pneus fora de alinhamento");
        assertEquals(StatusOrdemServico.DIAGNOSTICO, osComDiag.getStatus());

        // 6. Adicionar itens
        gerenciadorOS.adicionarItens(os.getId(), List.of(servico.getId()), List.of(peca.getId()), List.of(4));

        // 7. Gerar orçamento
        OrdemServico osOrcamento = gerenciadorOS.gerarOrcamento(os.getId());
        assertEquals(StatusOrdemServico.AGUARDANDO_APROVACAO, osOrcamento.getStatus());

        // 8. Aprovar orçamento
        gerenciadorOS.aprovarOrcamento(os.getId());
        OrdemServico osAprovada = gerenciadorOS.buscarPorId(os.getId()).orElseThrow();
        assertEquals(StatusOrdemServico.EXECUCAO, osAprovada.getStatus());
        assertNotNull(osAprovada.getDataInicioExecucao());

        // 9. Verificar baixa de estoque
        Peca pecaAtualizada = gerenciadorEstoque.buscarPorId(peca.getId()).orElseThrow();
        assertEquals(96, pecaAtualizada.getQuantidadeEstoque());

        // 10. Finalizar OS
        gerenciadorOS.finalizarOS(os.getId());
        OrdemServico osFinalizada = gerenciadorOS.buscarPorId(os.getId()).orElseThrow();
        assertEquals(StatusOrdemServico.FINALIZADA, osFinalizada.getStatus());
        assertNotNull(osFinalizada.getDataFinalizacao());

        // 11. Entregar OS
        gerenciadorOS.entregarOS(os.getId());
        OrdemServico osEntregue = gerenciadorOS.buscarPorId(os.getId()).orElseThrow();
        assertEquals(StatusOrdemServico.ENTREGUE, osEntregue.getStatus());
    }

    @Test
    void deveCalcularValorTotalDaOSCorretamente() {
        Cliente cliente = gerenciadorCliente.cadastrarCliente(
                "Pedro Teste", "11122233300", "pedro@email.com", "11977776666");
        gerenciadorCliente.adicionarVeiculo(cliente.getId(), "GHI3456", "Honda", "Civic", 2021);

        Servico servico = gerenciadorServico.cadastrarServico("Troca de Óleo", new BigDecimal("100.00"), 30);
        Peca peca = gerenciadorEstoque.cadastrarPeca("Óleo 5W30", new BigDecimal("50.00"), 20);

        OrdemServico os = gerenciadorOS.criarOS(cliente.getId(), "GHI3456");
        gerenciadorOS.registrarDiagnostico(os.getId(), "Troca necessária");
        gerenciadorOS.adicionarItens(os.getId(), List.of(servico.getId()), List.of(peca.getId()), List.of(2));

        OrdemServico osComItens = gerenciadorOS.buscarPorId(os.getId()).orElseThrow();
        BigDecimal esperado = new BigDecimal("200.00"); // 100 + (50 * 2)
        assertEquals(esperado, osComItens.getValorTotal());
    }

    @Test
    void deveBuscarOSPorCpfCnpjDoCliente() {
        Cliente cliente = gerenciadorCliente.cadastrarCliente(
                "Ana Teste", "55566677700", "ana@email.com", "11966665555");
        gerenciadorCliente.adicionarVeiculo(cliente.getId(), "JKL7890", "Chevrolet", "Onix", 2023);

        gerenciadorOS.criarOS(cliente.getId(), "JKL7890");

        List<OrdemServico> ordens = gerenciadorOS.buscarPorCpfCnpj("55566677700");
        assertFalse(ordens.isEmpty());
        assertEquals(cliente.getId(), ordens.get(0).getCliente().getId());
    }

    @Test
    void deveRetornarMonitoramentoComTempoMedio() {
        Cliente cliente = gerenciadorCliente.cadastrarCliente(
                "Carlos Teste", "44455566600", "carlos@email.com", "11955554444");
        gerenciadorCliente.adicionarVeiculo(cliente.getId(), "MNO1234", "Fiat", "Pulse", 2024);
        Peca peca = gerenciadorEstoque.cadastrarPeca("Filtro de Ar", new BigDecimal("25.00"), 10);

        OrdemServico os = gerenciadorOS.criarOS(cliente.getId(), "MNO1234");
        gerenciadorOS.registrarDiagnostico(os.getId(), "Filtro sujo");
        gerenciadorOS.adicionarItens(os.getId(), List.of(), List.of(peca.getId()), List.of(1));
        gerenciadorOS.gerarOrcamento(os.getId());
        gerenciadorOS.aprovarOrcamento(os.getId());
        gerenciadorOS.finalizarOS(os.getId());

        MonitoramentoData monitoramento = gerenciadorOS.getMonitoramento();
        assertEquals(1, monitoramento.totalOrdensFinalizadas());
        assertTrue(monitoramento.tempoMedioExecucaoMinutos() >= 0);
    }

    @Test
    void deveLancarExcecaoAoPularEtapaDoFluxo() {
        Cliente cliente = gerenciadorCliente.cadastrarCliente(
                "Lucas Teste", "77788899900", "lucas@email.com", "11944443333");
        gerenciadorCliente.adicionarVeiculo(cliente.getId(), "PQR5678", "VW", "Golf", 2020);

        OrdemServico os = gerenciadorOS.criarOS(cliente.getId(), "PQR5678");
        UUID osId = os.getId();

        // Tentar gerar orçamento sem passar por diagnóstico
        assertThrows(IllegalStateException.class, () -> gerenciadorOS.gerarOrcamento(osId));
    }

    @Test
    void deveCrudCompletoDeCliente() {
        Cliente criado = gerenciadorCliente.cadastrarCliente(
                "Fernanda Teste", "33344455500", "fernanda@email.com", "11933332222");
        assertNotNull(criado.getId());

        Cliente atualizado = gerenciadorCliente.atualizarCliente(
                criado.getId(), "Fernanda Atualizada", "nova@email.com", "11911110000");
        assertEquals("Fernanda Atualizada", atualizado.getNome());

        gerenciadorCliente.removerCliente(criado.getId());
        assertFalse(gerenciadorCliente.buscarPorId(criado.getId()).isPresent());
    }

    @Test
    void deveCrudCompletoDePeca() {
        Peca criada = gerenciadorEstoque.cadastrarPeca("Vela de Ignição", new BigDecimal("20.00"), 50);
        assertNotNull(criada.getId());

        Peca atualizada = gerenciadorEstoque.atualizarPeca(
                criada.getId(), "Vela NGK", new BigDecimal("25.00"), 60);
        assertEquals("Vela NGK", atualizada.getNome());

        gerenciadorEstoque.removerPeca(criada.getId());
        assertFalse(gerenciadorEstoque.buscarPorId(criada.getId()).isPresent());
    }

    @Test
    void deveCrudCompletoDeServico() {
        Servico criado = gerenciadorServico.cadastrarServico("Balanceamento", new BigDecimal("80.00"), 40);
        assertNotNull(criado.getId());

        Servico atualizado = gerenciadorServico.atualizarServico(
                criado.getId(), "Balanceamento Completo", new BigDecimal("90.00"), 50);
        assertEquals("Balanceamento Completo", atualizado.getNome());

        gerenciadorServico.removerServico(criado.getId());
        assertFalse(gerenciadorServico.buscarPorId(criado.getId()).isPresent());
    }
}

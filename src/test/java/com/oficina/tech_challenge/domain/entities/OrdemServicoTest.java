package com.oficina.tech_challenge.domain.entities;

import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrdemServicoTest {

    private Cliente cliente;
    private Veiculo veiculo;
    private Servico servico;
    private Peca peca;

    @BeforeEach
    void setUp() {
        cliente = new Cliente("João Silva", new CpfCnpj("123.456.789-00"), "joao@email.com", "11999999999");
        veiculo = new Veiculo("ABC1234", "Ford", "Fiesta", 2020);
        servico = new Servico("Troca de Óleo", new BigDecimal("150.00"), 30);
        peca = new Peca("Óleo 5W30", new BigDecimal("50.00"), 10);
    }

    @Test
    void deveCalcularValorTotalCorretamente() {
        OrdemServico os = new OrdemServico(cliente, veiculo);
        os.registrarDiagnostico("Necessária troca de óleo");
        os.adicionarServico(servico);
        os.adicionarPeca(peca, 2);

        BigDecimal esperado = new BigDecimal("250.00"); // 150 + (50 * 2)
        assertEquals(esperado, os.getValorTotal());
    }

    @Test
    void deveMudarStatusParaDiagnosticoAoRegistrarDiagnostico() {
        OrdemServico os = new OrdemServico(cliente, veiculo);
        os.registrarDiagnostico("Teste");
        assertEquals(StatusOrdemServico.DIAGNOSTICO, os.getStatus());
    }

    @Test
    void deveLancarExcecaoAoAprovarSemPassarPorDiagnostico() {
        OrdemServico os = new OrdemServico(cliente, veiculo);
        assertThrows(IllegalStateException.class, os::gerarOrcamento);
    }

    @Test
    void deveMudarStatusParaExecucaoAoAprovar() {
        OrdemServico os = new OrdemServico(cliente, veiculo);
        os.registrarDiagnostico("Teste");
        os.adicionarPeca(peca, 2);
        os.gerarOrcamento();
        os.aprovar();

        // Baixa de estoque é responsabilidade do GerenciadorOrdemServico (application service)
        assertEquals(10, peca.getQuantidadeEstoque());
        assertEquals(StatusOrdemServico.EXECUCAO, os.getStatus());
        assertNotNull(os.getDataInicioExecucao());
    }

    @Test
    void deveMudarStatusParaFinalizadaEEntregue() {
        OrdemServico os = new OrdemServico(cliente, veiculo);
        os.registrarDiagnostico("Teste");
        os.gerarOrcamento();
        os.aprovar();
        os.finalizar();
        assertEquals(StatusOrdemServico.FINALIZADA, os.getStatus());
        assertNotNull(os.getDataFinalizacao());

        os.entregar();
        assertEquals(StatusOrdemServico.ENTREGUE, os.getStatus());
    }
}

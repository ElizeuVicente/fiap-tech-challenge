package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.domain.entities.Servico;
import com.oficina.tech_challenge.domain.repositories.ServicoRepository;
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
class GerenciadorServicoTest {

    @Mock
    private ServicoRepository servicoRepository;

    @InjectMocks
    private GerenciadorServico gerenciadorServico;

    private Servico servico;
    private UUID servicoId;

    @BeforeEach
    void setUp() {
        servicoId = UUID.randomUUID();
        servico = new Servico("Troca de Óleo", new BigDecimal("150.00"), 30);
    }

    @Test
    void deveCadastrarServicoComSucesso() {
        when(servicoRepository.save(any())).thenReturn(servico);

        Servico resultado = gerenciadorServico.cadastrarServico("Troca de Óleo", new BigDecimal("150.00"), 30);

        assertNotNull(resultado);
        verify(servicoRepository).save(any(Servico.class));
    }

    @Test
    void deveLancarExcecaoAoCadastrarServicoSemNome() {
        assertThrows(IllegalArgumentException.class,
                () -> gerenciadorServico.cadastrarServico("", new BigDecimal("150.00"), 30));
    }

    @Test
    void deveLancarExcecaoAoCadastrarServicoComPrecoZero() {
        assertThrows(IllegalArgumentException.class,
                () -> gerenciadorServico.cadastrarServico("Troca de Óleo", BigDecimal.ZERO, 30));
    }

    @Test
    void deveAtualizarServicoComSucesso() {
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));
        when(servicoRepository.save(any())).thenReturn(servico);

        Servico resultado = gerenciadorServico.atualizarServico(servicoId, "Alinhamento", new BigDecimal("200.00"), 45);

        assertNotNull(resultado);
        verify(servicoRepository).save(servico);
    }

    @Test
    void deveLancarExcecaoAoAtualizarServicoInexistente() {
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> gerenciadorServico.atualizarServico(servicoId, "Nome", BigDecimal.ONE, 30));
    }

    @Test
    void deveRemoverServicoComSucesso() {
        when(servicoRepository.existsById(servicoId)).thenReturn(true);

        gerenciadorServico.removerServico(servicoId);

        verify(servicoRepository).deleteById(servicoId);
    }

    @Test
    void deveLancarExcecaoAoRemoverServicoInexistente() {
        when(servicoRepository.existsById(servicoId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> gerenciadorServico.removerServico(servicoId));
    }

    @Test
    void deveListarServicos() {
        when(servicoRepository.findAll()).thenReturn(List.of(servico));

        List<Servico> resultado = gerenciadorServico.listarServicos();

        assertEquals(1, resultado.size());
    }

    @Test
    void deveBuscarServicoPorId() {
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));

        Optional<Servico> resultado = gerenciadorServico.buscarPorId(servicoId);

        assertTrue(resultado.isPresent());
    }
}

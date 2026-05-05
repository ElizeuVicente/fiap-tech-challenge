package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.domain.entities.Peca;
import com.oficina.tech_challenge.domain.repositories.PecaRepository;
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
class GerenciadorEstoqueTest {

    @Mock
    private PecaRepository pecaRepository;

    @InjectMocks
    private GerenciadorEstoque gerenciadorEstoque;

    private Peca peca;
    private UUID pecaId;

    @BeforeEach
    void setUp() {
        pecaId = UUID.randomUUID();
        peca = new Peca("Óleo 5W30", new BigDecimal("50.00"), 10);
    }

    @Test
    void deveCadastrarPecaComSucesso() {
        when(pecaRepository.save(any())).thenReturn(peca);

        Peca resultado = gerenciadorEstoque.cadastrarPeca("Óleo 5W30", new BigDecimal("50.00"), 10);

        assertNotNull(resultado);
        verify(pecaRepository).save(any(Peca.class));
    }

    @Test
    void deveAtualizarPecaComSucesso() {
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));
        when(pecaRepository.save(any())).thenReturn(peca);

        Peca resultado = gerenciadorEstoque.atualizarPeca(pecaId, "Óleo 10W40", new BigDecimal("60.00"), 20);

        assertNotNull(resultado);
        verify(pecaRepository).save(peca);
    }

    @Test
    void deveLancarExcecaoAoAtualizarPecaInexistente() {
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> gerenciadorEstoque.atualizarPeca(pecaId, "Nome", BigDecimal.ONE, 5));
    }

    @Test
    void deveRemoverPecaComSucesso() {
        when(pecaRepository.existsById(pecaId)).thenReturn(true);

        gerenciadorEstoque.removerPeca(pecaId);

        verify(pecaRepository).deleteById(pecaId);
    }

    @Test
    void deveLancarExcecaoAoRemoverPecaInexistente() {
        when(pecaRepository.existsById(pecaId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> gerenciadorEstoque.removerPeca(pecaId));
    }

    @Test
    void deveAtualizarEstoqueAbatendoQuantidade() {
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));
        when(pecaRepository.save(any())).thenReturn(peca);

        gerenciadorEstoque.atualizarEstoque(pecaId, 3);

        assertEquals(7, peca.getQuantidadeEstoque());
        verify(pecaRepository).save(peca);
    }

    @Test
    void deveLancarExcecaoAoAbaterMaisDoQueEstoque() {
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));

        assertThrows(IllegalStateException.class, () -> gerenciadorEstoque.atualizarEstoque(pecaId, 20));
    }

    @Test
    void deveListarPecas() {
        when(pecaRepository.findAll()).thenReturn(List.of(peca));

        List<Peca> resultado = gerenciadorEstoque.listarPecas();

        assertEquals(1, resultado.size());
    }

    @Test
    void deveBuscarPecaPorId() {
        when(pecaRepository.findById(pecaId)).thenReturn(Optional.of(peca));

        Optional<Peca> resultado = gerenciadorEstoque.buscarPorId(pecaId);

        assertTrue(resultado.isPresent());
    }
}

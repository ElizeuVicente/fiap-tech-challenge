package com.oficina.tech_challenge.application.interfaces;

import com.oficina.tech_challenge.domain.entities.Peca;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IGerenciadorEstoque {
    Peca cadastrarPeca(String nome, BigDecimal preco, Integer quantidadeEstoque);
    Peca atualizarPeca(UUID id, String nome, BigDecimal preco, Integer quantidadeEstoque);
    void removerPeca(UUID id);
    void atualizarEstoque(UUID id, int quantidadeAbater);
    Optional<Peca> buscarPorId(UUID id);
    List<Peca> listarPecas();
}

package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorEstoque;
import com.oficina.tech_challenge.domain.entities.Peca;
import com.oficina.tech_challenge.domain.repositories.PecaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GerenciadorEstoque implements IGerenciadorEstoque {
    private final PecaRepository pecaRepository;

    public GerenciadorEstoque(PecaRepository pecaRepository) {
        this.pecaRepository = pecaRepository;
    }

    @Transactional
    public Peca cadastrarPeca(String nome, BigDecimal preco, Integer quantidadeEstoque) {
        Peca peca = new Peca(nome, preco, quantidadeEstoque);
        return pecaRepository.save(peca);
    }

    @Transactional
    public Peca atualizarPeca(UUID id, String nome, BigDecimal preco, Integer quantidadeEstoque) {
        Peca peca = pecaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Peça não encontrada"));
        peca.setNome(nome);
        peca.setPreco(preco);
        peca.setQuantidadeEstoque(quantidadeEstoque);
        return pecaRepository.save(peca);
    }

    @Transactional
    public void removerPeca(UUID id) {
        if (!pecaRepository.existsById(id)) {
            throw new IllegalArgumentException("Peça não encontrada");
        }
        pecaRepository.deleteById(id);
    }

    @Transactional
    public void atualizarEstoque(UUID id, int quantidadeAbater) {
        Peca peca = pecaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Peça não encontrada"));
        peca.baixarEstoque(quantidadeAbater);
        pecaRepository.save(peca);
    }

    public Optional<Peca> buscarPorId(UUID id) {
        return pecaRepository.findById(id);
    }

    public List<Peca> listarPecas() {
        return pecaRepository.findAll();
    }
}

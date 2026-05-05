package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorServico;
import com.oficina.tech_challenge.domain.entities.Servico;
import com.oficina.tech_challenge.domain.repositories.ServicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GerenciadorServico implements IGerenciadorServico {
    private final ServicoRepository servicoRepository;

    public GerenciadorServico(ServicoRepository servicoRepository) {
        this.servicoRepository = servicoRepository;
    }

    @Transactional
    public Servico cadastrarServico(String nome, BigDecimal precoBase, Integer tempoEstimado) {
        Servico servico = new Servico(nome, precoBase, tempoEstimado);
        return servicoRepository.save(servico);
    }

    @Transactional
    public Servico atualizarServico(UUID id, String nome, BigDecimal precoBase, Integer tempoEstimado) {
        Servico servico = servicoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado"));
        servico.setNome(nome);
        servico.setPrecoBase(precoBase);
        servico.setTempoEstimadoMinutos(tempoEstimado);
        return servicoRepository.save(servico);
    }

    @Transactional
    public void removerServico(UUID id) {
        if (!servicoRepository.existsById(id)) {
            throw new IllegalArgumentException("Serviço não encontrado");
        }
        servicoRepository.deleteById(id);
    }

    public Optional<Servico> buscarPorId(UUID id) {
        return servicoRepository.findById(id);
    }

    public List<Servico> listarServicos() {
        return servicoRepository.findAll();
    }
}

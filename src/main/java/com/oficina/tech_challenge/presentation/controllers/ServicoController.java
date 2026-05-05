package com.oficina.tech_challenge.presentation.controllers;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorServico;
import com.oficina.tech_challenge.domain.entities.Servico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {
    private final IGerenciadorServico gerenciadorServico;

    public ServicoController(IGerenciadorServico gerenciadorServico) {
        this.gerenciadorServico = gerenciadorServico;
    }

    @PostMapping
    public ResponseEntity<Servico> cadastrar(@Valid @RequestBody ServicoRequest request) {
        return ResponseEntity.ok(gerenciadorServico.cadastrarServico(
                request.getNome(), request.getPrecoBase(), request.getTempoEstimado()));
    }

    @GetMapping
    public ResponseEntity<List<Servico>> listar() {
        return ResponseEntity.ok(gerenciadorServico.listarServicos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Servico> buscar(@PathVariable UUID id) {
        return gerenciadorServico.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Servico> atualizar(@PathVariable UUID id, @Valid @RequestBody ServicoRequest request) {
        return ResponseEntity.ok(gerenciadorServico.atualizarServico(
                id, request.getNome(), request.getPrecoBase(), request.getTempoEstimado()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        gerenciadorServico.removerServico(id);
        return ResponseEntity.noContent().build();
    }

    public static class ServicoRequest {
        @NotBlank(message = "Nome é obrigatório")
        private String nome;

        @NotNull(message = "Preço base é obrigatório")
        @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
        private BigDecimal precoBase;

        @Positive(message = "Tempo estimado deve ser positivo")
        private Integer tempoEstimado;

        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public BigDecimal getPrecoBase() { return precoBase; }
        public void setPrecoBase(BigDecimal precoBase) { this.precoBase = precoBase; }
        public Integer getTempoEstimado() { return tempoEstimado; }
        public void setTempoEstimado(Integer tempoEstimado) { this.tempoEstimado = tempoEstimado; }
    }
}

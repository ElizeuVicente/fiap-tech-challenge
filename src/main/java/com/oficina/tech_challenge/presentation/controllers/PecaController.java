package com.oficina.tech_challenge.presentation.controllers;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorEstoque;
import com.oficina.tech_challenge.domain.entities.Peca;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pecas")
public class PecaController {
    private final IGerenciadorEstoque gerenciadorEstoque;

    public PecaController(IGerenciadorEstoque gerenciadorEstoque) {
        this.gerenciadorEstoque = gerenciadorEstoque;
    }

    @PostMapping
    public ResponseEntity<Peca> cadastrar(@Valid @RequestBody PecaRequest request) {
        return ResponseEntity.ok(gerenciadorEstoque.cadastrarPeca(
                request.getNome(), request.getPreco(), request.getQuantidadeEstoque()));
    }

    @GetMapping
    public ResponseEntity<List<Peca>> listar() {
        return ResponseEntity.ok(gerenciadorEstoque.listarPecas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Peca> buscar(@PathVariable UUID id) {
        return gerenciadorEstoque.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Peca> atualizar(@PathVariable UUID id, @Valid @RequestBody PecaRequest request) {
        return ResponseEntity.ok(gerenciadorEstoque.atualizarPeca(
                id, request.getNome(), request.getPreco(), request.getQuantidadeEstoque()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        gerenciadorEstoque.removerPeca(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/estoque")
    public ResponseEntity<Void> atualizarEstoque(@PathVariable UUID id, @RequestParam int quantidadeAbater) {
        gerenciadorEstoque.atualizarEstoque(id, quantidadeAbater);
        return ResponseEntity.ok().build();
    }

    public static class PecaRequest {
        @NotBlank(message = "Nome é obrigatório")
        private String nome;

        @NotNull(message = "Preço é obrigatório")
        @DecimalMin(value = "0.00", message = "Preço não pode ser negativo")
        private BigDecimal preco;

        @NotNull(message = "Quantidade em estoque é obrigatória")
        @Min(value = 0, message = "Quantidade em estoque não pode ser negativa")
        private Integer quantidadeEstoque;

        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public BigDecimal getPreco() { return preco; }
        public void setPreco(BigDecimal preco) { this.preco = preco; }
        public Integer getQuantidadeEstoque() { return quantidadeEstoque; }
        public void setQuantidadeEstoque(Integer quantidadeEstoque) { this.quantidadeEstoque = quantidadeEstoque; }
    }
}

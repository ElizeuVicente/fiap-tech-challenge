package com.oficina.tech_challenge.domain.entities;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Peca {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String nome;
    private BigDecimal preco;
    private Integer quantidadeEstoque;

    public Peca(String nome, BigDecimal preco, Integer quantidadeEstoque) {
        validateNome(nome);
        validatePreco(preco);
        validateEstoque(quantidadeEstoque);
        this.nome = nome;
        this.preco = preco;
        this.quantidadeEstoque = quantidadeEstoque;
    }

    private void validateNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da peça é obrigatório");
        }
    }

    private void validatePreco(BigDecimal preco) {
        if (preco == null || preco.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preço da peça não pode ser negativo");
        }
    }

    private void validateEstoque(Integer qtd) {
        if (qtd == null || qtd < 0) {
            throw new IllegalArgumentException("Quantidade em estoque não pode ser negativa");
        }
    }

    public void baixarEstoque(int quantidade) {
        if (this.quantidadeEstoque < quantidade) {
            throw new IllegalStateException("Estoque insuficiente para a peça: " + nome);
        }
        this.quantidadeEstoque -= quantidade;
    }

    public void adicionarEstoque(int quantidade) {
        this.quantidadeEstoque += quantidade;
    }
}

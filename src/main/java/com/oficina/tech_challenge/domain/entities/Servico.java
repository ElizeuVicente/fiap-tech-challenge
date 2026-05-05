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
public class Servico {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String nome;
    private BigDecimal precoBase;
    private Integer tempoEstimadoMinutos;

    public Servico(String nome, BigDecimal precoBase, Integer tempoEstimadoMinutos) {
        validateNome(nome);
        validatePreco(precoBase);
        this.nome = nome;
        this.precoBase = precoBase;
        this.tempoEstimadoMinutos = tempoEstimadoMinutos;
    }

    private void validateNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }
    }

    private void validatePreco(BigDecimal preco) {
        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Preço do serviço deve ser maior que zero");
        }
    }
}

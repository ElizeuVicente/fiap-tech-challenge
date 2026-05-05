package com.oficina.tech_challenge.domain.entities;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ItemPeca {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private Peca peca;

    private Integer quantidade;
    private BigDecimal precoAplicado;

    public ItemPeca(Peca peca, Integer quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade de peças deve ser positiva");
        }
        this.peca = peca;
        this.quantidade = quantidade;
        this.precoAplicado = peca.getPreco();
    }

    public BigDecimal getSubtotal() {
        return precoAplicado.multiply(BigDecimal.valueOf(quantidade));
    }
}

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

    private UUID referenciaExternaId;
    private String descricaoSnapshot;

    private Integer quantidade;
    private BigDecimal precoAplicado;

    public ItemPeca(Peca peca, Integer quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade de peças deve ser positiva");
        }
        this.peca = peca;
        this.referenciaExternaId = peca.getId();
        this.descricaoSnapshot = peca.getNome();
        this.quantidade = quantidade;
        this.precoAplicado = peca.getPreco();
    }

    public BigDecimal getSubtotal() {
        return precoAplicado.multiply(BigDecimal.valueOf(quantidade));
    }

    public UUID getReferenciaCatalogoId() {
        return referenciaExternaId != null ? referenciaExternaId : peca != null ? peca.getId() : null;
    }
    public ItemPeca(UUID referencia, String descricao, BigDecimal preco, int quantidade) {
        if (referencia == null || descricao == null || descricao.isBlank() || preco == null || preco.signum() <= 0 || quantidade <= 0)
            throw new IllegalArgumentException("Item de catálogo inválido");
        this.referenciaExternaId = referencia; this.descricaoSnapshot = descricao;
        this.precoAplicado = preco; this.quantidade = quantidade;
    }
    public String getDescricaoCatalogo() { return descricaoSnapshot != null ? descricaoSnapshot : peca != null ? peca.getNome() : null; }
}

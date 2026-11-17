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
public class ItemServico {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private Servico servico;

    private UUID referenciaExternaId;
    private String descricaoSnapshot;

    private BigDecimal precoAplicado;

    public ItemServico(Servico servico) {
        this.servico = servico;
        this.referenciaExternaId = servico.getId();
        this.descricaoSnapshot = servico.getNome();
        this.precoAplicado = servico.getPrecoBase();
    }

    public UUID getReferenciaCatalogoId() {
        return referenciaExternaId != null ? referenciaExternaId : servico != null ? servico.getId() : null;
    }
    public ItemServico(UUID referencia, String descricao, BigDecimal preco) {
        if (referencia == null || descricao == null || descricao.isBlank() || preco == null || preco.signum() <= 0)
            throw new IllegalArgumentException("Item de catálogo inválido");
        this.referenciaExternaId = referencia; this.descricaoSnapshot = descricao; this.precoAplicado = preco;
    }
    public String getDescricaoCatalogo() { return descricaoSnapshot != null ? descricaoSnapshot : servico != null ? servico.getNome() : null; }
}

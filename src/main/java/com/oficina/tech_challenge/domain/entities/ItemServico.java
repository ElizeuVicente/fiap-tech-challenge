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

    private BigDecimal precoAplicado;

    public ItemServico(Servico servico) {
        this.servico = servico;
        this.precoAplicado = servico.getPrecoBase();
    }
}

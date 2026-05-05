package com.oficina.tech_challenge.domain.entities;

import java.time.Year;
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
public class Veiculo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String placa;
    private String marca;
    private String modelo;
    private Integer ano;

    public Veiculo(String placa, String marca, String modelo, Integer ano) {
        validatePlaca(placa);
        validateAno(ano);
        this.placa = placa;
        this.marca = marca;
        this.modelo = modelo;
        this.ano = ano;
    }

    private void validatePlaca(String placa) {
        if (placa == null || !placa.matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}")) {
            throw new IllegalArgumentException("Placa inválida (Formato Mercosul ou Antigo)");
        }
    }

    private void validateAno(Integer ano) {
        int currentYear = Year.now().getValue();
        if (ano == null || ano < 1886 || ano > currentYear + 1) {
            throw new IllegalArgumentException("Ano do veículo inválido");
        }
    }
}

package com.oficina.tech_challenge.domain.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.NoArgsConstructor;
import lombok.Getter;

@Embeddable
@Getter
@NoArgsConstructor(force = true)
public class CpfCnpj {
    @Column(name = "cpf_cnpj")
    private final String value;

    public CpfCnpj(String value) {
        if (value == null || !isValid(value)) {
            throw new IllegalArgumentException("CPF/CNPJ inválido");
        }
        this.value = value.replaceAll("\\D", "");
    }

    private boolean isValid(String value) {
        String cleaned = value.replaceAll("\\D", "");
        return cleaned.length() == 11 || cleaned.length() == 14;
    }

    public String getValue() {
        return value;
    }

    public String getNumero() {
        return value;
    }

    public boolean isCpf() {
        return value != null && value.length() == 11;
    }

    public boolean isCnpj() {
        return value != null && value.length() == 14;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CpfCnpj cpfCnpj = (CpfCnpj) o;
        return Objects.equals(value, cpfCnpj.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

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
        if (cleaned.length() == 11) {
            return isValidCpf(cleaned);
        }
        if (cleaned.length() == 14) {
            return isValidCnpj(cleaned);
        }
        return false;
    }

    private boolean isValidCpf(String cpf) {
        if (hasAllDigitsEqual(cpf)) {
            return false;
        }

        int firstDigit = calculateDigit(cpf, 9, 10);
        int secondDigit = calculateDigit(cpf, 10, 11);

        return Character.getNumericValue(cpf.charAt(9)) == firstDigit
                && Character.getNumericValue(cpf.charAt(10)) == secondDigit;
    }

    private boolean isValidCnpj(String cnpj) {
        if (hasAllDigitsEqual(cnpj)) {
            return false;
        }

        int firstDigit = calculateCnpjDigit(cnpj, 12);
        int secondDigit = calculateCnpjDigit(cnpj, 13);

        return Character.getNumericValue(cnpj.charAt(12)) == firstDigit
                && Character.getNumericValue(cnpj.charAt(13)) == secondDigit;
    }

    private int calculateDigit(String value, int length, int weight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Character.getNumericValue(value.charAt(i)) * (weight - i);
        }
        int result = 11 - (sum % 11);
        return result >= 10 ? 0 : result;
    }

    private int calculateCnpjDigit(String cnpj, int length) {
        int[] weights = length == 12
                ? new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
                : new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Character.getNumericValue(cnpj.charAt(i)) * weights[i];
        }
        int result = sum % 11;
        return result < 2 ? 0 : 11 - result;
    }

    private boolean hasAllDigitsEqual(String value) {
        return value.chars().distinct().count() == 1;
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

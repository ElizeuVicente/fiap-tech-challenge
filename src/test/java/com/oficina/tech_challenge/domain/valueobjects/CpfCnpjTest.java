package com.oficina.tech_challenge.domain.valueobjects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CpfCnpjTest {

    @Test
    void deveAceitarCpfValidoComFormatacao() {
        CpfCnpj cpf = new CpfCnpj("123.456.789-09");
        assertEquals("12345678909", cpf.getNumero());
    }

    @Test
    void deveAceitarCnpjValidoSemFormatacao() {
        CpfCnpj cnpj = new CpfCnpj("12345678901230");
        assertEquals("12345678901230", cnpj.getNumero());
    }

    @Test
    void deveLancarExcecaoParaNumeroInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new CpfCnpj("123"));
        assertThrows(IllegalArgumentException.class, () -> new CpfCnpj("1234567890a"));
        assertThrows(IllegalArgumentException.class, () -> new CpfCnpj("12345678900"));
        assertThrows(IllegalArgumentException.class, () -> new CpfCnpj("11111111111"));
        assertThrows(IllegalArgumentException.class, () -> new CpfCnpj("12345678901234"));
        assertThrows(IllegalArgumentException.class, () -> new CpfCnpj("00000000000000"));
    }

    @Test
    void deveLancarExcecaoParaNull() {
        assertThrows(IllegalArgumentException.class, () -> new CpfCnpj(null));
    }

    @Test
    void deveIdentificarCpf() {
        CpfCnpj cpf = new CpfCnpj("12345678909");
        assertTrue(cpf.isCpf());
        assertFalse(cpf.isCnpj());
    }

    @Test
    void deveIdentificarCnpj() {
        CpfCnpj cnpj = new CpfCnpj("12345678901230");
        assertTrue(cnpj.isCnpj());
        assertFalse(cnpj.isCpf());
    }

    @Test
    void deveRetornarValueCorretamente() {
        CpfCnpj cpf = new CpfCnpj("12345678909");
        assertEquals("12345678909", cpf.getValue());
    }

    @Test
    void deveImplementarEqualsEHashCode() {
        CpfCnpj cpf1 = new CpfCnpj("12345678909");
        CpfCnpj cpf2 = new CpfCnpj("123.456.789-09");
        CpfCnpj outro = new CpfCnpj("98765432100");

        assertEquals(cpf1, cpf2);
        assertEquals(cpf1.hashCode(), cpf2.hashCode());
        assertNotEquals(cpf1, outro);
    }

    @Test
    void deveImplementarToString() {
        CpfCnpj cpf = new CpfCnpj("12345678909");
        assertEquals("12345678909", cpf.toString());
    }
}

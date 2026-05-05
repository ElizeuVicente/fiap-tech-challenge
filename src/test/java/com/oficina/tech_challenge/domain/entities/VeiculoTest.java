package com.oficina.tech_challenge.domain.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VeiculoTest {

    @Test
    void deveAceitarPlacaPadraoNovo() {
        Veiculo v = new Veiculo("ABC1C34", "Ford", "Fiesta", 2020);
        assertEquals("ABC1C34", v.getPlaca());
    }

    @Test
    void deveAceitarPlacaPadraoAntigo() {
        Veiculo v = new Veiculo("ABC1234", "Ford", "Fiesta", 2020);
        assertEquals("ABC1234", v.getPlaca());
    }

    @Test
    void deveLancarExcecaoParaPlacaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new Veiculo("ABC12345", "Ford", "Fiesta", 2020));
        assertThrows(IllegalArgumentException.class, () -> new Veiculo("AB1234", "Ford", "Fiesta", 2020));
    }

    @Test
    void deveLancarExcecaoParaAnoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new Veiculo("ABC1234", "Ford", "Fiesta", 1800));
        assertThrows(IllegalArgumentException.class, () -> new Veiculo("ABC1234", "Ford", "Fiesta", 2100));
    }
}

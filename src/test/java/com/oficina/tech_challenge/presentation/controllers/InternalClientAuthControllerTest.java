package com.oficina.tech_challenge.presentation.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.oficina.tech_challenge.domain.entities.Cliente;
import com.oficina.tech_challenge.domain.repositories.ClienteRepository;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalClientAuthControllerTest {
    @Test void requiresTokenBeforeAccessAndReturnsOnlyAuthFields() {
        var repo = mock(ClienteRepository.class);
        String token = "local-test-token-01234567890123456789";
        var controller = new InternalClientAuthController(repo, token);
        var request = new InternalClientAuthController.Request("12345678909");
        assertThrows(ResponseStatusException.class, () -> controller.lookup("wrong", request));
        verifyNoInteractions(repo);
        var client = new Cliente("Cliente teste", new CpfCnpj("12345678909"), "a@example.com", "123456789");
        when(repo.findByCpfCnpj(any())).thenReturn(Optional.of(client));
        var body = controller.lookup(token, request).getBody();
        assertNotNull(body);
        assertEquals("12345678909", body.cpf());
        assertEquals("ATIVO", body.status());
    }
}

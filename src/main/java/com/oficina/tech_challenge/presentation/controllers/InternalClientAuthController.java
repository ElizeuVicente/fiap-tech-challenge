package com.oficina.tech_challenge.presentation.controllers;

import com.oficina.tech_challenge.domain.repositories.ClienteRepository;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Internal lookup for the authentication service; no database credentials leave OS. */
@RestController
@RequestMapping("/internal/auth")
@ConditionalOnProperty(name = "oficina.internal-auth.enabled", havingValue = "true")
class InternalClientAuthController {
    record Request(String cpf) { }
    record Response(UUID id, String cpf, String status) { }
    private final ClienteRepository clients;
    private final byte[] secret;
    InternalClientAuthController(ClienteRepository clients, @Value("${oficina.internal-auth.service-token}") String secret) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("Internal auth token must have at least 32 characters");
        this.clients = clients;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    @PostMapping("/client-lookup")
    ResponseEntity<Response> lookup(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestBody Request request) {
        if (token == null || !MessageDigest.isEqual(secret, token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (request == null || request.cpf() == null || !request.cpf().matches("[0-9]{11}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var client = clients.findByCpfCnpj(new CpfCnpj(request.cpf()));
        return client.map(c -> ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new Response(c.getId(), request.cpf(), c.getStatus().name())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

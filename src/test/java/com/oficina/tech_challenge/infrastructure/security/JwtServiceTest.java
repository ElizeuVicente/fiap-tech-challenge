package com.oficina.tech_challenge.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {
    private static final String SECRET = "64656661756c747365637265746b65796d75737462657374726f6e6765727468616e74686973313233343536";

    @Test
    void acceptsValidClientToken() {
        JwtService service = service();
        String token = token("CLIENTE", "oficina-auth", "oficina-api", new Date(System.currentTimeMillis() + 60_000));
        assertTrue(service.isClientTokenValid(token));
        assertEquals("52998224725", service.extractClientCpf(token));
    }

    @Test
    void rejectsWrongAudienceAndExpiredToken() {
        JwtService service = service();
        assertFalse(service.isClientTokenValid(token("CLIENTE", "oficina-auth", "outra-api", new Date(System.currentTimeMillis() + 60_000))));
        assertFalse(service.isClientTokenValid(token("CLIENTE", "oficina-auth", "oficina-api", new Date(System.currentTimeMillis() - 1_000))));
    }

    private JwtService service() { JwtService service = new JwtService(); ReflectionTestUtils.setField(service, "secretKey", SECRET); return service; }
    private String token(String role, String issuer, String audience, Date expiration) {
        return Jwts.builder().subject("cliente-id").issuer(issuer).audience().add(audience).and()
                .claim("role", role).claim("cpf", "52998224725").expiration(expiration)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}

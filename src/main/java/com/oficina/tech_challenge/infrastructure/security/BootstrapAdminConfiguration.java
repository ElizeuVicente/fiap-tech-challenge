package com.oficina.tech_challenge.infrastructure.security;

import com.oficina.tech_challenge.application.services.GerenciadorAutenticacao;
import com.oficina.tech_challenge.domain.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cria o primeiro administrador somente quando explicitamente configurado. */
@Configuration
public class BootstrapAdminConfiguration {

    @Bean
    ApplicationRunner bootstrapAdmin(
            UsuarioRepository usuarios,
            GerenciadorAutenticacao autenticacao,
            @Value("${security.bootstrap.username:}") String username,
            @Value("${security.bootstrap.password:}") String password) {
        return args -> {
            if (username.isBlank() && password.isBlank()) {
                return;
            }
            if (username.isBlank() || password.length() < 12) {
                throw new IllegalStateException("Bootstrap ADMIN exige username e password com ao menos 12 caracteres");
            }
            if (usuarios.count() == 0) {
                autenticacao.registrar(username, password);
            }
        };
    }
}

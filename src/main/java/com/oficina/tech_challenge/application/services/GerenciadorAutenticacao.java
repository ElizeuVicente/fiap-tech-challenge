package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.domain.entities.Usuario;
import com.oficina.tech_challenge.domain.repositories.UsuarioRepository;
import com.oficina.tech_challenge.infrastructure.security.JwtService;
import com.oficina.tech_challenge.infrastructure.security.UsuarioUserDetails;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GerenciadorAutenticacao {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public GerenciadorAutenticacao(UsuarioRepository repository, PasswordEncoder passwordEncoder,
            JwtService jwtService, AuthenticationManager authenticationManager) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public void registrar(String username, String password) {
        Usuario user = Usuario.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role("ROLE_ADMIN")
                .build();
        repository.save(user);
    }

    public String login(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        Usuario user = repository.findByUsername(username).orElseThrow();
        return jwtService.generateToken(new UsuarioUserDetails(user));
    }
}

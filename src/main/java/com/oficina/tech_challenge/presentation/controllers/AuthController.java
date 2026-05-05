package com.oficina.tech_challenge.presentation.controllers;

import com.oficina.tech_challenge.application.services.GerenciadorAutenticacao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final GerenciadorAutenticacao gerenciadorAutenticacao;

    public AuthController(GerenciadorAutenticacao gerenciadorAutenticacao) {
        this.gerenciadorAutenticacao = gerenciadorAutenticacao;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody AuthRequest request) {
        gerenciadorAutenticacao.registrar(request.getUsername(), request.getPassword());
        return ResponseEntity.ok("Usuário registrado com sucesso");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody AuthRequest request) {
        String token = gerenciadorAutenticacao.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(token);
    }

    public static class AuthRequest {
        private String username;
        private String password;

        // Getters & Setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}

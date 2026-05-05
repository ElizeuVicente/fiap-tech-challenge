package com.oficina.tech_challenge.presentation.controllers;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorCliente;
import com.oficina.tech_challenge.domain.entities.Cliente;
import com.oficina.tech_challenge.domain.entities.Veiculo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {
    private final IGerenciadorCliente gerenciadorCliente;

    public ClienteController(IGerenciadorCliente gerenciadorCliente) {
        this.gerenciadorCliente = gerenciadorCliente;
    }

    @PostMapping
    public ResponseEntity<Cliente> cadastrar(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(gerenciadorCliente.cadastrarCliente(
                request.getNome(), request.getCpfCnpj(), request.getEmail(), request.getTelefone()));
    }

    @GetMapping
    public ResponseEntity<List<Cliente>> listar() {
        return ResponseEntity.ok(gerenciadorCliente.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cliente> buscar(@PathVariable UUID id) {
        return gerenciadorCliente.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cliente> atualizar(@PathVariable UUID id, @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(gerenciadorCliente.atualizarCliente(
                id, request.getNome(), request.getEmail(), request.getTelefone()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        gerenciadorCliente.removerCliente(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/veiculos")
    public ResponseEntity<List<Veiculo>> listarVeiculos(@PathVariable UUID id) {
        return ResponseEntity.ok(gerenciadorCliente.listarVeiculos(id));
    }

    @PostMapping("/{id}/veiculos")
    public ResponseEntity<Void> adicionarVeiculo(@PathVariable UUID id, @Valid @RequestBody VeiculoRequest request) {
        gerenciadorCliente.adicionarVeiculo(id, request.getPlaca(), request.getMarca(), request.getModelo(), request.getAno());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/veiculos/{veiculoId}")
    public ResponseEntity<Veiculo> atualizarVeiculo(@PathVariable UUID id, @PathVariable UUID veiculoId,
            @Valid @RequestBody VeiculoUpdateRequest request) {
        return ResponseEntity.ok(gerenciadorCliente.atualizarVeiculo(
                id, veiculoId, request.getMarca(), request.getModelo(), request.getAno()));
    }

    @DeleteMapping("/{id}/veiculos/{veiculoId}")
    public ResponseEntity<Void> removerVeiculo(@PathVariable UUID id, @PathVariable UUID veiculoId) {
        gerenciadorCliente.removerVeiculo(id, veiculoId);
        return ResponseEntity.noContent().build();
    }

    public static class ClienteRequest {
        @NotBlank(message = "Nome é obrigatório")
        private String nome;

        @NotBlank(message = "CPF/CNPJ é obrigatório")
        private String cpfCnpj;

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        private String email;

        @NotBlank(message = "Telefone é obrigatório")
        private String telefone;

        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getCpfCnpj() { return cpfCnpj; }
        public void setCpfCnpj(String cpfCnpj) { this.cpfCnpj = cpfCnpj; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getTelefone() { return telefone; }
        public void setTelefone(String telefone) { this.telefone = telefone; }
    }

    public static class VeiculoRequest {
        @NotBlank(message = "Placa é obrigatória")
        private String placa;

        @NotBlank(message = "Marca é obrigatória")
        private String marca;

        @NotBlank(message = "Modelo é obrigatório")
        private String modelo;

        @NotNull(message = "Ano é obrigatório")
        private Integer ano;

        public String getPlaca() { return placa; }
        public void setPlaca(String placa) { this.placa = placa; }
        public String getMarca() { return marca; }
        public void setMarca(String marca) { this.marca = marca; }
        public String getModelo() { return modelo; }
        public void setModelo(String modelo) { this.modelo = modelo; }
        public Integer getAno() { return ano; }
        public void setAno(Integer ano) { this.ano = ano; }
    }

    public static class VeiculoUpdateRequest {
        @NotBlank(message = "Marca é obrigatória")
        private String marca;

        @NotBlank(message = "Modelo é obrigatório")
        private String modelo;

        @NotNull(message = "Ano é obrigatório")
        private Integer ano;

        public String getMarca() { return marca; }
        public void setMarca(String marca) { this.marca = marca; }
        public String getModelo() { return modelo; }
        public void setModelo(String modelo) { this.modelo = modelo; }
        public Integer getAno() { return ano; }
        public void setAno(Integer ano) { this.ano = ano; }
    }
}

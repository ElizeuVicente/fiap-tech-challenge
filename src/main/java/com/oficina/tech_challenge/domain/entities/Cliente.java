package com.oficina.tech_challenge.domain.entities;

import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nome;

    @Embedded
    private CpfCnpj cpfCnpj;

    private String email;
    private String telefone;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cliente_id")
    private List<Veiculo> veiculos = new ArrayList<>();

    public Cliente(String nome, CpfCnpj cpfCnpj, String email, String telefone) {
        validateNome(nome);
        validateEmail(email);
        this.nome = nome;
        this.cpfCnpj = cpfCnpj;
        this.email = email;
        this.telefone = telefone;
    }

    private void validateNome(String nome) {
        if (nome == null || nome.trim().length() < 3) {
            throw new IllegalArgumentException("Nome do cliente deve ter pelo menos 3 caracteres");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Email inválido");
        }
    }

    public void adicionarVeiculo(Veiculo veiculo) {
        if (veiculo == null) {
            throw new IllegalArgumentException("Veículo não pode ser nulo");
        }
        this.veiculos.add(veiculo);
    }
}

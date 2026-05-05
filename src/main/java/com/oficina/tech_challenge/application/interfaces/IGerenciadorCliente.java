package com.oficina.tech_challenge.application.interfaces;

import com.oficina.tech_challenge.domain.entities.Cliente;
import com.oficina.tech_challenge.domain.entities.Veiculo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IGerenciadorCliente {
    Cliente cadastrarCliente(String nome, String cpfCnpj, String email, String telefone);
    Cliente atualizarCliente(UUID id, String nome, String email, String telefone);
    void removerCliente(UUID id);
    void adicionarVeiculo(UUID clienteId, String placa, String marca, String modelo, Integer ano);
    Veiculo atualizarVeiculo(UUID clienteId, UUID veiculoId, String marca, String modelo, Integer ano);
    void removerVeiculo(UUID clienteId, UUID veiculoId);
    List<Veiculo> listarVeiculos(UUID clienteId);
    List<Cliente> listarTodos();
    Optional<Cliente> buscarPorId(UUID id);
    Optional<Cliente> buscarPorCpfCnpj(String cpfCnpj);
}

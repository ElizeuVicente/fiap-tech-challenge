package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorCliente;
import com.oficina.tech_challenge.domain.entities.Cliente;
import com.oficina.tech_challenge.domain.entities.Veiculo;
import com.oficina.tech_challenge.domain.repositories.ClienteRepository;
import com.oficina.tech_challenge.domain.repositories.VeiculoRepository;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GerenciadorCliente implements IGerenciadorCliente {
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;

    public GerenciadorCliente(ClienteRepository clienteRepository, VeiculoRepository veiculoRepository) {
        this.clienteRepository = clienteRepository;
        this.veiculoRepository = veiculoRepository;
    }

    @Transactional
    public Cliente cadastrarCliente(String nome, String cpfCnpj, String email, String telefone) {
        CpfCnpj vo = new CpfCnpj(cpfCnpj);
        if (clienteRepository.findByCpfCnpj(vo).isPresent()) {
            throw new IllegalArgumentException("Cliente já cadastrado com este CPF/CNPJ");
        }
        Cliente cliente = new Cliente(nome, vo, email, telefone);
        return clienteRepository.save(cliente);
    }

    @Transactional
    public Cliente atualizarCliente(UUID id, String nome, String email, String telefone) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        cliente.setNome(nome);
        cliente.setEmail(email);
        cliente.setTelefone(telefone);
        return clienteRepository.save(cliente);
    }

    @Transactional
    public void removerCliente(UUID id) {
        if (!clienteRepository.existsById(id)) {
            throw new IllegalArgumentException("Cliente não encontrado");
        }
        clienteRepository.deleteById(id);
    }

    @Transactional
    public void adicionarVeiculo(UUID clienteId, String placa, String marca, String modelo, Integer ano) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        Veiculo veiculo = new Veiculo(placa, marca, modelo, ano);
        cliente.adicionarVeiculo(veiculo);
        clienteRepository.save(cliente);
    }

    @Transactional
    public Veiculo atualizarVeiculo(UUID clienteId, UUID veiculoId, String marca, String modelo, Integer ano) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        Veiculo veiculo = cliente.getVeiculos().stream()
                .filter(v -> v.getId().equals(veiculoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado para este cliente"));
        veiculo.setMarca(marca);
        veiculo.setModelo(modelo);
        veiculo.setAno(ano);
        return veiculoRepository.save(veiculo);
    }

    @Transactional
    public void removerVeiculo(UUID clienteId, UUID veiculoId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        Veiculo veiculo = cliente.getVeiculos().stream()
                .filter(v -> v.getId().equals(veiculoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado para este cliente"));
        cliente.getVeiculos().remove(veiculo);
        clienteRepository.save(cliente);
    }

    public List<Veiculo> listarVeiculos(UUID clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        return cliente.getVeiculos();
    }

    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> buscarPorId(UUID id) {
        return clienteRepository.findById(id);
    }

    public Optional<Cliente> buscarPorCpfCnpj(String cpfCnpj) {
        return clienteRepository.findByCpfCnpj(new CpfCnpj(cpfCnpj));
    }
}

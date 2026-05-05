package com.oficina.tech_challenge.application.services;

import com.oficina.tech_challenge.domain.entities.Cliente;
import com.oficina.tech_challenge.domain.entities.Veiculo;
import com.oficina.tech_challenge.domain.repositories.ClienteRepository;
import com.oficina.tech_challenge.domain.repositories.VeiculoRepository;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GerenciadorClienteTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private VeiculoRepository veiculoRepository;

    @InjectMocks
    private GerenciadorCliente gerenciadorCliente;

    private Cliente cliente;
    private UUID clienteId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();
        cliente = new Cliente("João Silva", new CpfCnpj("12345678900"), "joao@email.com", "11999999999");
    }

    @Test
    void deveCadastrarClienteComSucesso() {
        when(clienteRepository.findByCpfCnpj(any())).thenReturn(Optional.empty());
        when(clienteRepository.save(any())).thenReturn(cliente);

        Cliente resultado = gerenciadorCliente.cadastrarCliente("João Silva", "12345678900", "joao@email.com", "11999999999");

        assertNotNull(resultado);
        verify(clienteRepository).save(any(Cliente.class));
    }

    @Test
    void deveLancarExcecaoAoCadastrarClienteDuplicado() {
        when(clienteRepository.findByCpfCnpj(any())).thenReturn(Optional.of(cliente));

        assertThrows(IllegalArgumentException.class,
                () -> gerenciadorCliente.cadastrarCliente("João", "12345678900", "joao@email.com", "11999"));
    }

    @Test
    void deveAtualizarClienteComSucesso() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any())).thenReturn(cliente);

        Cliente resultado = gerenciadorCliente.atualizarCliente(clienteId, "João Atualizado", "novo@email.com", "11888888888");

        assertNotNull(resultado);
        verify(clienteRepository).save(cliente);
    }

    @Test
    void deveLancarExcecaoAoAtualizarClienteInexistente() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> gerenciadorCliente.atualizarCliente(clienteId, "Nome", "email@email.com", "11999"));
    }

    @Test
    void deveRemoverClienteComSucesso() {
        when(clienteRepository.existsById(clienteId)).thenReturn(true);

        gerenciadorCliente.removerCliente(clienteId);

        verify(clienteRepository).deleteById(clienteId);
    }

    @Test
    void deveLancarExcecaoAoRemoverClienteInexistente() {
        when(clienteRepository.existsById(clienteId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> gerenciadorCliente.removerCliente(clienteId));
    }

    @Test
    void deveAdicionarVeiculoAoCliente() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any())).thenReturn(cliente);

        gerenciadorCliente.adicionarVeiculo(clienteId, "ABC1234", "Ford", "Fiesta", 2020);

        verify(clienteRepository).save(cliente);
        assertEquals(1, cliente.getVeiculos().size());
    }

    @Test
    void deveLancarExcecaoAoRemoverVeiculoInexistente() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));

        UUID idInexistente = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> gerenciadorCliente.removerVeiculo(clienteId, idInexistente));
    }

    @Test
    void deveListarTodosOsClientes() {
        when(clienteRepository.findAll()).thenReturn(List.of(cliente));

        List<Cliente> resultado = gerenciadorCliente.listarTodos();

        assertEquals(1, resultado.size());
    }

    @Test
    void deveBuscarClientePorCpfCnpj() {
        when(clienteRepository.findByCpfCnpj(any())).thenReturn(Optional.of(cliente));

        Optional<Cliente> resultado = gerenciadorCliente.buscarPorCpfCnpj("12345678900");

        assertTrue(resultado.isPresent());
    }
}

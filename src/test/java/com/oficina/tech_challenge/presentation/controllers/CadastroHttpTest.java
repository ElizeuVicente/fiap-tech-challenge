package com.oficina.tech_challenge.presentation.controllers;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorCliente;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorEstoque;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorServico;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contratos HTTP de cadastro: validação antes de alterações e respostas de consulta. */
class CadastroHttpTest {
    @Test void clienteESeusVeiculosRespeitamValidacao() throws Exception {
        var service = mock(IGerenciadorCliente.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ClienteController(service)).build();
        String id = UUID.randomUUID().toString();
        mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        mvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"cpfCnpj\":\"12345678901\",\"email\":\"ana@example.org\",\"telefone\":\"11999999999\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/clientes/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(get("/api/clientes/" + id + "/veiculos")).andExpect(status().isOk());
        mvc.perform(post("/api/clientes/" + id + "/veiculos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"placa\":\"ABC1234\",\"marca\":\"A\",\"modelo\":\"B\",\"ano\":2020}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/clientes/" + id + "/veiculos/" + UUID.randomUUID())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/clientes/" + id)).andExpect(status().isNoContent());
    }

    @Test void catalogoRecusaValoresInvalidosEPermiteConsulta() throws Exception {
        var parts = mock(IGerenciadorEstoque.class);
        var services = mock(IGerenciadorServico.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PecaController(parts), new ServicoController(services)).build();
        String id = UUID.randomUUID().toString();
        mvc.perform(post("/api/pecas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Filtro\",\"preco\":-1,\"quantidadeEstoque\":2}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(parts);
        mvc.perform(post("/api/servicos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Troca\",\"precoBase\":0,\"tempoEstimado\":10}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(services);
        mvc.perform(get("/api/pecas/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/servicos/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pecas")).andExpect(status().isOk());
        mvc.perform(get("/api/servicos")).andExpect(status().isOk());
        mvc.perform(delete("/api/pecas/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/servicos/" + id)).andExpect(status().isNoContent());
    }
}

package com.oficina.tech_challenge.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorCliente;
import com.oficina.tech_challenge.application.services.GerenciadorAutenticacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "oficina.branch-access.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BranchAuthorizationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired IGerenciadorCliente clientes;
    @Autowired GerenciadorAutenticacao auth;
    @Autowired com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico ordens;
    @Autowired ObjectMapper json;

    @Test void administratorCanOnlyOpenAndAccessOrdersOfAuthorizedBranch() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS usuario_filial (username VARCHAR(255), filial_id UUID, PRIMARY KEY(username,filial_id))");
        String username = "branch-admin-" + UUID.randomUUID();
        auth.registrar(username, "senha-segura-de-teste");
        String token = auth.login(username, "senha-segura-de-teste");
        UUID branch = UUID.randomUUID(), other = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario_filial(username,filial_id) VALUES (?,?)", username, branch);
        var customer = clientes.cadastrarCliente("Cliente", "98765432100", "cliente@example.org", "11999999999");
        clientes.adicionarVeiculo(customer.getId(), "BRN1A23", "Marca", "Modelo", 2020);
        ordens.criarOS(customer.getId(), "BRN1A23", other);
        String body = "{\"clienteId\":\"" + customer.getId() + "\",\"placa\":\"BRN1A23\",\"filialId\":\"" + branch + "\"}";
        var response = mvc.perform(post("/api/ordens-servico").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse();
        UUID order = UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText());
        mvc.perform(get("/api/ordens-servico").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(order.toString()));
        mvc.perform(get("/api/ordens-servico?operacional=true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/ordens-servico/{id}", order).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/ordens-servico").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body.replace(branch.toString(), other.toString()))).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM usuario_filial WHERE username=?", username);
        mvc.perform(get("/api/ordens-servico/{id}", order).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/ordens-servico/{id}/diagnostico", order).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"diagnostico\":\"teste\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/ordens-servico").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/clientes").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pecas").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/servicos").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }
}

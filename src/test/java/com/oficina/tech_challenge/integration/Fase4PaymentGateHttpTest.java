package com.oficina.tech_challenge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorCliente;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.application.services.GerenciadorAutenticacao;
import com.oficina.tech_challenge.domain.entities.StatusOrdemServico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "oficina.fase4.payment-gate=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Fase4PaymentGateHttpTest {
    @Autowired MockMvc mvc;
    @Autowired IGerenciadorCliente clientes;
    @Autowired IGerenciadorOrdemServico ordens;
    @Autowired GerenciadorAutenticacao auth;

    @Test void administrativeApprovalCannotStartExecutionOrSkipGate() throws Exception {
        auth.registrar("gate-admin", "senha-forte-gate");
        String token = auth.login("gate-admin", "senha-forte-gate");
        var cliente = clientes.cadastrarCliente("Cliente Gate", "52998224725", "gate@example.com", "11999999999");
        clientes.adicionarVeiculo(cliente.getId(), "GAT1A23", "Fiat", "Argo", 2022);
        var order = ordens.criarOS(cliente.getId(), "GAT1A23");
        ordens.registrarDiagnostico(order.getId(), "Diagnóstico");
        ordens.gerarOrcamento(order.getId());
        mvc.perform(patch("/api/ordens-servico/{id}/aprovar", order.getId()).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals(StatusOrdemServico.AGUARDANDO_PAGAMENTO, ordens.buscarPorId(order.getId()).orElseThrow().getStatus());
        mvc.perform(post("/api/ordens-servico/{id}/status", order.getId()).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"novoStatus\":\"EXECUCAO\",\"origem\":\"API\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(StatusOrdemServico.AGUARDANDO_PAGAMENTO, ordens.buscarPorId(order.getId()).orElseThrow().getStatus());
    }
}

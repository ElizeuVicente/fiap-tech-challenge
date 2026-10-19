package com.oficina.tech_challenge.integration;

import com.oficina.tech_challenge.application.interfaces.IGerenciadorCliente;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.application.services.GerenciadorAutenticacao;
import com.oficina.tech_challenge.domain.entities.Cliente;
import com.oficina.tech_challenge.domain.entities.OrdemServico;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutorizacaoApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private IGerenciadorCliente clientes;
    @Autowired private IGerenciadorOrdemServico ordens;
    @Autowired private GerenciadorAutenticacao autenticacao;
    @Value("${security.jwt.secret}") private String jwtSecret;

    @Test
    void clienteAcessaSomenteSuasOrdensEnaoRotasAdministrativas() throws Exception {
        Cliente dono = clientes.cadastrarCliente("Maria Teste", "98765432100", "maria@email.com", "11988887777");
        clientes.adicionarVeiculo(dono.getId(), "DEF5G67", "Toyota", "Corolla", 2022);
        OrdemServico ordem = ordens.criarOS(dono.getId(), "DEF5G67");
        String tokenDono = tokenCliente("98765432100");
        String tokenOutro = tokenCliente("55566677720");

        mvc.perform(get("/api/ordens-servico/{id}", ordem.getId()).header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ordens-servico/{id}/status", ordem.getId()).header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ordens-servico/cliente/{cpf}", "98765432100").header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk());

        mvc.perform(get("/api/ordens-servico/{id}", ordem.getId()).header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ordens-servico/{id}/status", ordem.getId()).header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ordens-servico/cliente/{cpf}", "98765432100").header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes").header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ordens-servico").header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/ordens-servico/{id}/aprovar", ordem.getId()).header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").header("Authorization", "Bearer " + tokenDono)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"intruso\",\"password\":\"senha-qualquer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registroAdministrativoNaoPodeSerAnonimo() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"intruso\",\"password\":\"senha-qualquer\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void administradorAutenticadoPodeRegistrarOutroAdministrador() throws Exception {
        autenticacao.registrar("admin-original", "senha-original-forte");
        String tokenAdmin = autenticacao.login("admin-original", "senha-original-forte");
        mvc.perform(post("/api/auth/register")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin-novo\",\"password\":\"nova-senha-forte\"}"))
                .andExpect(status().isOk());
    }

    private String tokenCliente(String cpf) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("oficina-auth")
                .audience().add("oficina-api").and()
                .claim("cpf", cpf)
                .claim("role", "CLIENTE")
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}

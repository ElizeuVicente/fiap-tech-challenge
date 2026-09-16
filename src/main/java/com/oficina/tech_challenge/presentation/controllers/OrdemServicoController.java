package com.oficina.tech_challenge.presentation.controllers;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.application.dtos.MetricasNegocioData;
import com.oficina.tech_challenge.application.dtos.AberturaOrdemServicoCommand;
import com.oficina.tech_challenge.application.dtos.AtualizacaoStatusCommand;
import com.oficina.tech_challenge.application.dtos.NotificacaoOrcamentoCommand;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.domain.entities.DecisaoOrcamento;
import com.oficina.tech_challenge.domain.entities.StatusOrdemServico;
import com.oficina.tech_challenge.presentation.dtos.OrdemServicoResponse;
import com.oficina.tech_challenge.presentation.dtos.OrdemServicoStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordens-servico")
public class OrdemServicoController {
    private final IGerenciadorOrdemServico gerenciadorOS;
    private final String webhookSecret;

    public OrdemServicoController(IGerenciadorOrdemServico gerenciadorOS,
            @Value("${external.webhook.secret}") String webhookSecret) {
        this.gerenciadorOS = gerenciadorOS;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping
    public ResponseEntity<OrdemServicoResponse> criar(@RequestBody CriarOSRequest request) {
        if (request.getCliente() != null || request.getVeiculo() != null) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(OrdemServicoResponse.from(gerenciadorOS.abrirOrdemServico(request.toCommand())));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrdemServicoResponse.from(gerenciadorOS.criarOS(request.getClienteId(), request.getPlaca())));
    }

    @GetMapping
    public ResponseEntity<List<OrdemServicoResponse>> listar(
            @RequestParam(name = "operacional", defaultValue = "false") boolean operacional) {
        List<OrdemServicoResponse> ordens = (operacional ? gerenciadorOS.listarOperacionais() : gerenciadorOS.listarTodas())
                .stream()
                .map(OrdemServicoResponse::from)
                .toList();
        return ResponseEntity.ok(ordens);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponse> buscar(@PathVariable UUID id) {
        return gerenciadorOS.buscarPorId(id)
                .map(os -> ResponseEntity.ok(OrdemServicoResponse.from(os)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<OrdemServicoStatusResponse> buscarStatus(@PathVariable UUID id) {
        return gerenciadorOS.buscarPorId(id)
                .map(os -> ResponseEntity.ok(OrdemServicoStatusResponse.from(os)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{cpfCnpj}")
    public ResponseEntity<List<OrdemServicoResponse>> buscarPorCliente(@PathVariable String cpfCnpj, Authentication authentication) {
        boolean cliente = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
        if (cliente && !cpfCnpj.replaceAll("\\D", "").equals(authentication.getPrincipal())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(gerenciadorOS.buscarPorCpfCnpj(cpfCnpj).stream().map(OrdemServicoResponse::from).toList());
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<Void> adicionarItens(@PathVariable UUID id, @RequestBody ItensOSRequest request) {
        gerenciadorOS.adicionarItens(id, request.getServicoIds(), request.getPecaIds(), request.getQuantidades());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/diagnostico")
    public ResponseEntity<OrdemServicoResponse> registrarDiagnostico(@PathVariable UUID id,
            @RequestBody DiagnosticoRequest request) {
        return ResponseEntity.ok(OrdemServicoResponse.from(gerenciadorOS.registrarDiagnostico(id, request.getDiagnostico())));
    }

    @PatchMapping("/{id}/orcamento")
    public ResponseEntity<OrdemServicoResponse> gerarOrcamento(@PathVariable UUID id) {
        return ResponseEntity.ok(OrdemServicoResponse.from(gerenciadorOS.gerarOrcamento(id)));
    }

    @PatchMapping("/{id}/aprovar")
    public ResponseEntity<Void> aprovar(@PathVariable UUID id) {
        gerenciadorOS.aprovarOrcamento(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/orcamento/notificacoes")
    public ResponseEntity<OrdemServicoResponse> processarNotificacaoOrcamento(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Webhook-Secret", required = false) String secret,
            @RequestBody NotificacaoOrcamentoRequest request) {
        validarWebhookSecret(secret);
        return ResponseEntity.ok(OrdemServicoResponse.from(
                gerenciadorOS.processarNotificacaoOrcamento(id, request.toCommand())));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<OrdemServicoResponse> atualizarStatus(
            @PathVariable UUID id,
            @RequestBody AtualizacaoStatusRequest request) {
        return ResponseEntity.ok(OrdemServicoResponse.from(gerenciadorOS.atualizarStatus(id, request.toCommand())));
    }

    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<Void> finalizar(@PathVariable UUID id) {
        gerenciadorOS.finalizarOS(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/entregar")
    public ResponseEntity<Void> entregar(@PathVariable UUID id) {
        gerenciadorOS.entregarOS(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/monitoramento")
    public ResponseEntity<MonitoramentoData> listarMonitoramento() {
        return ResponseEntity.ok(gerenciadorOS.getMonitoramento());
    }

    @GetMapping("/metricas-negocio")
    public ResponseEntity<MetricasNegocioData> metricasNegocio() { return ResponseEntity.ok(gerenciadorOS.getMetricasNegocio()); }

    public static class ItensOSRequest {
        private List<UUID> servicoIds;
        private List<UUID> pecaIds;
        private List<Integer> quantidades;

        public List<UUID> getServicoIds() { return servicoIds; }
        public void setServicoIds(List<UUID> servicoIds) { this.servicoIds = servicoIds; }
        public List<UUID> getPecaIds() { return pecaIds; }
        public void setPecaIds(List<UUID> pecaIds) { this.pecaIds = pecaIds; }
        public List<Integer> getQuantidades() { return quantidades; }
        public void setQuantidades(List<Integer> quantidades) { this.quantidades = quantidades; }
    }

    public static class DiagnosticoRequest {
        private String diagnostico;

        public String getDiagnostico() { return diagnostico; }
        public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
    }

    public static class CriarOSRequest {
        private UUID clienteId;
        private String placa;
        private ClienteRequest cliente;
        private VeiculoRequest veiculo;
        private List<ServicoRequest> servicos;
        private List<PecaRequest> pecas;

        public UUID getClienteId() { return clienteId; }
        public void setClienteId(UUID clienteId) { this.clienteId = clienteId; }
        public String getPlaca() { return placa; }
        public void setPlaca(String placa) { this.placa = placa; }
        public ClienteRequest getCliente() { return cliente; }
        public void setCliente(ClienteRequest cliente) { this.cliente = cliente; }
        public VeiculoRequest getVeiculo() { return veiculo; }
        public void setVeiculo(VeiculoRequest veiculo) { this.veiculo = veiculo; }
        public List<ServicoRequest> getServicos() { return servicos; }
        public void setServicos(List<ServicoRequest> servicos) { this.servicos = servicos; }
        public List<PecaRequest> getPecas() { return pecas; }
        public void setPecas(List<PecaRequest> pecas) { this.pecas = pecas; }

        AberturaOrdemServicoCommand toCommand() {
            return new AberturaOrdemServicoCommand(
                    new AberturaOrdemServicoCommand.ClienteData(
                            cliente.nome,
                            cliente.cpfCnpj,
                            cliente.email,
                            cliente.telefone),
                    new AberturaOrdemServicoCommand.VeiculoData(
                            veiculo.placa,
                            veiculo.marca,
                            veiculo.modelo,
                            veiculo.ano),
                    servicos == null ? List.of() : servicos.stream()
                            .map(item -> new AberturaOrdemServicoCommand.ServicoData(item.servicoId))
                            .toList(),
                    pecas == null ? List.of() : pecas.stream()
                            .map(item -> new AberturaOrdemServicoCommand.PecaData(item.pecaId, item.quantidade))
                            .toList());
        }
    }

    public static class ClienteRequest {
        private String nome;
        private String cpfCnpj;
        private String email;
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
        private String placa;
        private String marca;
        private String modelo;
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

    public static class ServicoRequest {
        private UUID servicoId;

        public UUID getServicoId() { return servicoId; }
        public void setServicoId(UUID servicoId) { this.servicoId = servicoId; }
    }

    public static class PecaRequest {
        private UUID pecaId;
        private Integer quantidade;

        public UUID getPecaId() { return pecaId; }
        public void setPecaId(UUID pecaId) { this.pecaId = pecaId; }
        public Integer getQuantidade() { return quantidade; }
        public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
    }

    public record NotificacaoOrcamentoRequest(
            DecisaoOrcamento decisao,
            String origem,
            LocalDateTime dataHora,
            String identificadorExterno
    ) {
        NotificacaoOrcamentoCommand toCommand() {
            return new NotificacaoOrcamentoCommand(decisao, origem, dataHora, identificadorExterno);
        }
    }

    public record AtualizacaoStatusRequest(StatusOrdemServico novoStatus, String origem) {
        AtualizacaoStatusCommand toCommand() {
            return new AtualizacaoStatusCommand(novoStatus, origem);
        }
    }

    private void validarWebhookSecret(String secret) {
        if (!webhookSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook secret inválido");
        }
    }
}

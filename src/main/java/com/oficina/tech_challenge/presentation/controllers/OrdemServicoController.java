package com.oficina.tech_challenge.presentation.controllers;

import com.oficina.tech_challenge.application.dtos.MonitoramentoData;
import com.oficina.tech_challenge.application.interfaces.IGerenciadorOrdemServico;
import com.oficina.tech_challenge.presentation.dtos.OrdemServicoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordens-servico")
public class OrdemServicoController {
    private final IGerenciadorOrdemServico gerenciadorOS;

    public OrdemServicoController(IGerenciadorOrdemServico gerenciadorOS) {
        this.gerenciadorOS = gerenciadorOS;
    }

    @PostMapping
    public ResponseEntity<OrdemServicoResponse> criar(@RequestBody CriarOSRequest request) {
        return ResponseEntity.ok(OrdemServicoResponse.from(gerenciadorOS.criarOS(request.getClienteId(), request.getPlaca())));
    }

    @GetMapping
    public ResponseEntity<List<OrdemServicoResponse>> listar() {
        return ResponseEntity.ok(gerenciadorOS.listarTodas().stream().map(OrdemServicoResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdemServicoResponse> buscar(@PathVariable UUID id) {
        return gerenciadorOS.buscarPorId(id)
                .map(os -> ResponseEntity.ok(OrdemServicoResponse.from(os)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Endpoint público para o cliente acompanhar suas OS pelo CPF/CNPJ */
    @GetMapping("/cliente/{cpfCnpj}")
    public ResponseEntity<List<OrdemServicoResponse>> buscarPorCliente(@PathVariable String cpfCnpj) {
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

        public UUID getClienteId() { return clienteId; }
        public void setClienteId(UUID clienteId) { this.clienteId = clienteId; }
        public String getPlaca() { return placa; }
        public void setPlaca(String placa) { this.placa = placa; }
    }
}

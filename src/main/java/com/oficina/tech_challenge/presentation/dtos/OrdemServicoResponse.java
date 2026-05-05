package com.oficina.tech_challenge.presentation.dtos;

import com.oficina.tech_challenge.domain.entities.OrdemServico;
import com.oficina.tech_challenge.domain.entities.StatusOrdemServico;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrdemServicoResponse(
        UUID id,
        StatusOrdemServico status,
        String diagnostico,
        LocalDateTime dataCriacao,
        LocalDateTime dataInicioExecucao,
        LocalDateTime dataFinalizacao,
        BigDecimal valorTotal,
        ClienteResumo cliente,
        VeiculoResumo veiculo,
        List<ItemServicoResponse> servicos,
        List<ItemPecaResponse> pecas
) {
    public record ClienteResumo(UUID id, String nome, String cpfCnpj) {}
    public record VeiculoResumo(UUID id, String placa, String marca, String modelo, Integer ano) {}
    public record ItemServicoResponse(UUID id, String nomeServico, BigDecimal precoAplicado) {}
    public record ItemPecaResponse(UUID id, String nomePeca, Integer quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {}

    public static OrdemServicoResponse from(OrdemServico os) {
        ClienteResumo clienteResumo = new ClienteResumo(
                os.getCliente().getId(),
                os.getCliente().getNome(),
                os.getCliente().getCpfCnpj().getValue()
        );

        VeiculoResumo veiculoResumo = new VeiculoResumo(
                os.getVeiculo().getId(),
                os.getVeiculo().getPlaca(),
                os.getVeiculo().getMarca(),
                os.getVeiculo().getModelo(),
                os.getVeiculo().getAno()
        );

        List<ItemServicoResponse> servicos = os.getServicos().stream()
                .map(i -> new ItemServicoResponse(
                        i.getId(),
                        i.getServico().getNome(),
                        i.getPrecoAplicado()))
                .toList();

        List<ItemPecaResponse> pecas = os.getPecas().stream()
                .map(i -> new ItemPecaResponse(
                        i.getId(),
                        i.getPeca().getNome(),
                        i.getQuantidade(),
                        i.getPrecoAplicado(),
                        i.getSubtotal()))
                .toList();

        return new OrdemServicoResponse(
                os.getId(),
                os.getStatus(),
                os.getDiagnostico(),
                os.getDataCriacao(),
                os.getDataInicioExecucao(),
                os.getDataFinalizacao(),
                os.getValorTotal(),
                clienteResumo,
                veiculoResumo,
                servicos,
                pecas
        );
    }
}

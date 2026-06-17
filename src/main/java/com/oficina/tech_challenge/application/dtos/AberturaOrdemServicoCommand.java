package com.oficina.tech_challenge.application.dtos;

import java.util.List;
import java.util.UUID;

public record AberturaOrdemServicoCommand(
        ClienteData cliente,
        VeiculoData veiculo,
        List<ServicoData> servicos,
        List<PecaData> pecas
) {
    public record ClienteData(String nome, String cpfCnpj, String email, String telefone) {}
    public record VeiculoData(String placa, String marca, String modelo, Integer ano) {}
    public record ServicoData(UUID servicoId) {}
    public record PecaData(UUID pecaId, Integer quantidade) {}
}

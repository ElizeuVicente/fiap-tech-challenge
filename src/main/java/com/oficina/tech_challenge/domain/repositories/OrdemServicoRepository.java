package com.oficina.tech_challenge.domain.repositories;

import com.oficina.tech_challenge.domain.entities.OrdemServico;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrdemServicoRepository extends JpaRepository<OrdemServico, UUID> {
    List<OrdemServico> findByClienteCpfCnpj(CpfCnpj cpfCnpj);

    @Query("""
            SELECT o FROM OrdemServico o
            WHERE o.status NOT IN (
                com.oficina.tech_challenge.domain.entities.StatusOrdemServico.FINALIZADA,
                com.oficina.tech_challenge.domain.entities.StatusOrdemServico.ENTREGUE,
                com.oficina.tech_challenge.domain.entities.StatusOrdemServico.RECUSADA
            )
            ORDER BY CASE
                WHEN o.status = com.oficina.tech_challenge.domain.entities.StatusOrdemServico.EXECUCAO THEN 1
                WHEN o.status = com.oficina.tech_challenge.domain.entities.StatusOrdemServico.AGUARDANDO_APROVACAO THEN 2
                WHEN o.status = com.oficina.tech_challenge.domain.entities.StatusOrdemServico.DIAGNOSTICO THEN 3
                WHEN o.status = com.oficina.tech_challenge.domain.entities.StatusOrdemServico.RECEBIDA THEN 4
                ELSE 5
            END ASC, o.dataCriacao ASC
            """)
    List<OrdemServico> findOperacionaisOrdenadas();
}

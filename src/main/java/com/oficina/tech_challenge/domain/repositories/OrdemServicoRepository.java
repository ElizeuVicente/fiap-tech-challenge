package com.oficina.tech_challenge.domain.repositories;

import com.oficina.tech_challenge.domain.entities.OrdemServico;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrdemServicoRepository extends JpaRepository<OrdemServico, UUID> {
    List<OrdemServico> findByClienteCpfCnpj(CpfCnpj cpfCnpj);
}

package com.oficina.tech_challenge.domain.repositories;

import com.oficina.tech_challenge.domain.entities.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ServicoRepository extends JpaRepository<Servico, UUID> {
}

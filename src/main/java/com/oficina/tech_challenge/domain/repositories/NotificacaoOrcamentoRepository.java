package com.oficina.tech_challenge.domain.repositories;

import com.oficina.tech_challenge.domain.entities.NotificacaoOrcamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificacaoOrcamentoRepository extends JpaRepository<NotificacaoOrcamento, UUID> {
    Optional<NotificacaoOrcamento> findByIdentificadorExterno(String identificadorExterno);
}

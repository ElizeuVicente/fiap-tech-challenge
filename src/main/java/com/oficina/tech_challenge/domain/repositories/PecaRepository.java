package com.oficina.tech_challenge.domain.repositories;

import com.oficina.tech_challenge.domain.entities.Peca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PecaRepository extends JpaRepository<Peca, UUID> {
}

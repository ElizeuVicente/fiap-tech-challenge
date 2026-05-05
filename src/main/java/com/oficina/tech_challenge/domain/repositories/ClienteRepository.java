package com.oficina.tech_challenge.domain.repositories;

import com.oficina.tech_challenge.domain.entities.Cliente;
import com.oficina.tech_challenge.domain.valueobjects.CpfCnpj;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    Optional<Cliente> findByCpfCnpj(CpfCnpj cpfCnpj);
}

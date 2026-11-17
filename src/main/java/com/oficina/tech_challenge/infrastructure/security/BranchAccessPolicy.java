package com.oficina.tech_challenge.infrastructure.security;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in branch checks for administrative OS operations. */
@Component
public class BranchAccessPolicy {
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public BranchAccessPolicy(JdbcTemplate jdbc, @Value("${oficina.branch-access.enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.enabled = enabled;
    }

    public void validateCreation(Authentication authentication, UUID branchId) {
        if (!enabled) return;
        if (authentication == null || !authentication.isAuthenticated() || branchId == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Filial autorizada é obrigatória");
        Integer count = jdbc.queryForObject("SELECT count(*) FROM usuario_filial WHERE username=? AND filial_id=?",
                Integer.class, authentication.getName(), branchId);
        if (count == null || count != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso à filial não permitido");
    }

    public void validateOrder(Authentication authentication, UUID orderId) {
        if (!enabled) return;
        if (authentication == null || !authentication.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso à filial não permitido");
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM ordem_servico o JOIN usuario_filial f ON f.filial_id=o.filial_id
                WHERE o.id=? AND f.username=?
                """, Integer.class, orderId, authentication.getName());
        if (count == null || count != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso à filial não permitido");
    }

    public void validateBulkAccess() {
        if (enabled) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consulta geral não disponível com proteção por filial");
    }

    public Optional<List<UUID>> allowedBranches(Authentication authentication) {
        if (!enabled) return Optional.empty();
        if (authentication == null || !authentication.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso à filial não permitido");
        return Optional.of(jdbc.query("SELECT filial_id FROM usuario_filial WHERE username=?",
                (rs, row) -> rs.getObject(1, UUID.class), authentication.getName()));
    }
}

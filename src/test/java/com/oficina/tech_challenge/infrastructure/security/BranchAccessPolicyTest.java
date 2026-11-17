package com.oficina.tech_challenge.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class BranchAccessPolicyTest {
    @Test void creationRequiresExplicitUserBranchMembership() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var policy = new BranchAccessPolicy(jdbc, true);
        var auth = UsernamePasswordAuthenticationToken.authenticated("admin", null, java.util.List.of());
        UUID branch = UUID.randomUUID(), other = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("admin"), eq(branch))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("admin"), eq(other))).thenReturn(0);
        assertDoesNotThrow(() -> policy.validateCreation(auth, branch));
        assertThrows(ResponseStatusException.class, () -> policy.validateCreation(auth, other));
        assertThrows(ResponseStatusException.class, () -> policy.validateCreation(auth, null));
        assertThrows(ResponseStatusException.class, () -> policy.validateCreation(null, branch));
    }
    @Test void orderAccessRequiresMembershipAndBulkQueriesFailClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var policy = new BranchAccessPolicy(jdbc, true);
        var auth = UsernamePasswordAuthenticationToken.authenticated("admin", null, java.util.List.of());
        UUID own = UUID.randomUUID(), other = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(own), eq("admin"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(other), eq("admin"))).thenReturn(0);
        assertDoesNotThrow(() -> policy.validateOrder(auth, own));
        assertThrows(ResponseStatusException.class, () -> policy.validateOrder(auth, other));
        assertThrows(ResponseStatusException.class, policy::validateBulkAccess);
    }
}

package com.oficina.tech_challenge.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Restricts administrative order routes; customer access is checked by the controller. */
@Configuration
class BranchOrderInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final BranchAccessPolicy policy;
    BranchOrderInterceptor(BranchAccessPolicy policy) { this.policy = policy; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/ordens-servico/**", "/api/ordens-servico",
                "/api/clientes/**", "/api/clientes", "/api/pecas/**", "/api/pecas", "/api/servicos/**", "/api/servicos");
    }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/api/ordens-servico") && !path.startsWith("/api/ordens-servico/")) {
            // Legacy records have no branch owner; deny access until branch-scoped APIs exist.
            policy.validateBulkAccess();
            return true;
        }
        if (path.equals("/api/ordens-servico") && "POST".equals(request.getMethod())) return true;
        if (path.equals("/api/ordens-servico") && "GET".equals(request.getMethod())) return true;
        String suffix = path.substring("/api/ordens-servico".length());
        String first = suffix.startsWith("/") ? suffix.substring(1).split("/", 2)[0] : "";
        try { policy.validateOrder(auth, UUID.fromString(first)); }
        catch (IllegalArgumentException notOrderId) { policy.validateBulkAccess(); }
        return true;
    }
}

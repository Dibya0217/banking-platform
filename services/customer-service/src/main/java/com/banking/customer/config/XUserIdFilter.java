package com.banking.customer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * In dev/test mode: trusts the X-User-Id and X-User-Roles headers set by the API Gateway.
 * In production these headers are stripped from external requests by the gateway.
 */
@Component
public class XUserIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String rolesHeader = request.getHeader("X-User-Roles");

        if (userId != null && !userId.isBlank()) {
            List<SimpleGrantedAuthority> authorities = rolesHeader == null || rolesHeader.isBlank()
                    ? List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                    : Arrays.stream(rolesHeader.split(","))
                            .map(String::trim)
                            .map(SimpleGrantedAuthority::new)
                            .toList();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}

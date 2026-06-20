package com.example.reading.config;

import com.example.reading.service.AuthContextService;
import com.example.reading.service.AuthTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private AuthContextService authContextService;



    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = authTokenService.resolveToken(request);

        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = authContextService.currentUserId(token);
        if (userId == null) {
            // 设计决策：token 无效/过期时不在此处返回 401，而是放行给 Spring Security
            // 的 authorizeHttpRequests 规则决定。这样 permitAll() 路径不会因携带过期
            // token 而被误拦截。受保护路径仍会由 authenticationEntryPoint 返回 401。
            filterChain.doFilter(request, response);
            return;
        }

        String authority = authContextService.isAdmin(userId) ? "ROLE_ADMIN" : "ROLE_USER";
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, token,
                        List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}

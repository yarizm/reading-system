package com.example.reading.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // ASYNC: permitAll is required for SseEmitter endpoints. Spring MVC re-dispatches
                        // the request with ASYNC dispatcher type during async processing. With this rule,
                        // ASYNC dispatch bypasses security filters. Authentication is enforced at the
                        // controller level via authContextService.currentUserId().
                        // ERROR: permitAll is required for Spring Boot's default error handling.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/auth/**",
                                "/sysUser/login",
                                "/sysUser/register",
                                "/sysUser/profile/*",
                                "/ws/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/sysBook/list",
                                "/sysBook/hot",
                                "/sysBook/rank",
                                "/sysBook/recommend",
                                "/sysBook/*",
                                "/sysBook/catalog/*",
                                "/sysBook/chapter/*",
                                "/search/**",
                                "/files/**",
                                "/comment/list/*",
                                "/paragraphComment/list/*",
                                "/booklist/share/*"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("{\"code\":\"401\",\"msg\":\"未登录或登录已过期\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"code\":\"403\",\"msg\":\"权限不足\"}");
                        })
                );
        return http.build();
    }
}

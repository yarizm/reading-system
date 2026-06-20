package com.example.reading.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // 允许的 CORS Origin（逗号分隔），生产环境务必配置为实际域名，如：
    // CORS_ALLOWED_ORIGINS=https://example.com,https://admin.example.com
    @Value("${cors.allowed-origins:*}")
    private String corsAllowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(corsAllowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-User-Token"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
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
                                "/sysUser/profile/**",
                                "/ws/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/sysBook/list",
                                "/sysBook/hot",
                                "/sysBook/rank",
                                "/sysBook/recommend",
                                "/sysBook/*",
                                "/sysBook/catalog/**",
                                "/sysBook/chapter/**",
                                "/search/**",
                                "/files/**",
                                "/comment/list/**",
                                "/paragraphComment/list/**",
                                "/booklist/share/**"
                        ).permitAll()
                        .requestMatchers("/sysUser/list", "/sysUser/adminUpdate", "/sysUser/ban/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/sysUser/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/sysBook/add").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/sysBook/update").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/sysBook/**").hasRole("ADMIN")
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

    // 防止 JwtAuthenticationFilter 被 Servlet 容器自动注册导致双重执行，
    // 仅通过上方 addFilterBefore() 纳入 Spring Security filter chain。
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

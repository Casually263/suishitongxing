package com.suishi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suishi.common.Result;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean FilterRegistrationBean<JwtAuthenticationFilter> preventDoubleRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
    @Bean UrlBasedCorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var config = new CorsConfiguration(); config.setAllowedOrigins(properties.allowedOrigins()); config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token", "X-Request-Id", "Accept-Language"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, JwtAuthenticationFilter jwt, ObjectMapper json, Environment environment) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll();
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/portal/auth/register", "/api/portal/auth/login", "/api/admin/auth/login",
                            "/api/portal/auth/csrf", "/api/admin/auth/csrf", "/api/portal/auth/refresh", "/api/admin/auth/refresh",
                            "/api/portal/auth/logout", "/api/admin/auth/logout", "/api/common/meta", "/actuator/health").permitAll();
                    if (environment.acceptsProfiles(Profiles.of("dev", "test"))) auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    auth.requestMatchers("/api/admin/**").hasAuthority("admin:access").anyRequest().authenticated();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8"); json.writeValue(response.getWriter(), Result.error("UNAUTHENTICATED", "请先登录"));
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/json;charset=UTF-8"); json.writeValue(response.getWriter(), Result.error("FORBIDDEN", "没有访问此资源的权限"));
                }))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

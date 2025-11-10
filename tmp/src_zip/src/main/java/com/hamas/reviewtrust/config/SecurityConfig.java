// src/main/java/com/hamas/reviewtrust/config/SecurityConfig.java
package com.hamas.reviewtrust.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * セキュリティ設定：
 * - /api/admin/login は公開、それ以外の /api/admin/** は ROLE_ADMIN 必須
 * - OAuth2 Resource Server (JWT) を使用（HS256 / raw UTF-8 secret）
 * - 401 は JSON 定形で返却
 * - PasswordEncoder は Delegating（{bcrypt} 付き/無しの両対応）
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.jwt.secret:dev-secret-at-least-32-chars}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write(
              "{\"error\":{\"code\":\"E_AUTH\",\"message\":\"Unauthorized\",\"hint\":\"Bearer token invalid or missing\"}}"
            );
        };

        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(jwt -> JwtTokenService.extractAuthorities(jwt).stream()
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList())
        );

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/admin/login").permitAll()
                .requestMatchers("/api/admin/whoami").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                .authenticationEntryPoint(entryPoint)
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint));

        return http.build();
    }

    /** PasswordEncoder（委譲）— {bcrypt} 付き/無しの両対応 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        String idForEncode = "bcrypt";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());

        DelegatingPasswordEncoder d = new DelegatingPasswordEncoder(idForEncode, encoders);
        // 接頭辞なし（$2a$... など）の既存ハッシュは BCrypt として照合
        d.setDefaultPasswordEncoderForMatches(encoders.get("bcrypt"));
        return d;
    }

    /** HS256 の Decoder（raw UTF-8 secret 前提） */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** HS256 の Encoder */
    @Bean
    public JwtEncoder jwtEncoder() {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key.getEncoded()));
    }
}

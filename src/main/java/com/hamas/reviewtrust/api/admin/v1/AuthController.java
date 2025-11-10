package com.hamas.reviewtrust.api.admin.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.hamas.reviewtrust.config.JwtTokenService;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authManager;
    private final JwtTokenService jwt;

    public AuthController(AuthenticationConfiguration cfg, JwtTokenService jwt) throws Exception {
        this.authManager = cfg.getAuthenticationManager();
        this.jwt = jwt;
    }

    @PostMapping("/api/admin/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest in) {
        log.info("Login request received: username='{}' email='{}'", in.username, in.email);

        String id = StringUtils.hasText(in.username) ? in.username : in.email;
        if (!StringUtils.hasText(id) || !StringUtils.hasText(in.password)) {
            log.warn("Login failed: missing id or password");
            return unauthorized("E_BAD_REQUEST", "username/email and password are required");
        }

        try {
            log.debug("Attempting authentication for id='{}'", id);
            Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(id, in.password)
            );
            log.info("Authentication succeeded for '{}'", auth.getName());

            List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .toList();

            String token = jwt.create(auth.getName(), roles);
            log.info("JWT generated for '{}': {}", auth.getName(), token);

            return ResponseEntity.ok(Map.of(
                "token", token,
                "tokenType", "Bearer",
                "expiresIn", 3600,
                "sub", auth.getName(),
                "roles", roles
            ));
        } catch (Exception e) {
            log.warn("Login failed for '{}': {}", id, e.getMessage());
            return unauthorized("E_CREDENTIALS", "Bad credentials");
        }
    }

    @GetMapping("/api/admin/whoami")
    public ResponseEntity<?> whoami() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            log.warn("whoami failed: not authenticated");
            return unauthorized("E_AUTH", "Unauthorized");
        }

        log.info("whoami request from '{}'", a.getName());

        return ResponseEntity.ok(Map.of(
            "sub", a.getName(),
            "roles", a.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
        ));
    }

    private ResponseEntity<Map<String,Object>> unauthorized(String code, String msg){
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", Map.of("code", code, "message", msg)));
    }

    public static final class LoginRequest {
        public final String username;
        public final String email;
        public final String password;

        @JsonCreator
        public LoginRequest(
                @JsonProperty("username") String username,
                @JsonProperty("email") String email,
                @JsonProperty("password") String password) {
            this.username = username;
            this.email = email;
            this.password = password;
        }
    }
}

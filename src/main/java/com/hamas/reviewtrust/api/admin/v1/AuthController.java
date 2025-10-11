// AuthController.java (placeholder)
package com.hamas.reviewtrust.api.admin.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 管理ログインの最小口。
 * - Basic 認証ヘッダ または JSON ボディ {email,password} を受け付ける。
 * - 成功時: 200 {"status":"ok","user":{email,roles:["ADMIN"]}}
 * - 失敗時: 401 を GlobalExceptionHandler が {error:{code,message,hint}} で整形
 *   （MVPのエラーフォーマットに準拠） 
 */
@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        this.authenticationManager = authenticationConfiguration.getAuthenticationManager();
    }

    /** デフォルトは /api/admin/login（admin.login-path で変更可） */
    @PostMapping("${admin.login-path:/api/admin/login}")
    public ResponseEntity<?> login(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) LoginRequest body
    ) {
        Cred cred = extract(authorization, body);
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(cred.email(), cred.password()));
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "user", Map.of(
                            "email", auth.getName(),
                            "roles", List.of("ADMIN")
                    )));
        } catch (AuthenticationException ex) {
            // 統一エラーフォーマットは GlobalExceptionHandler に委譲
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    private Cred extract(String authorization, LoginRequest body) {
        // Basic ヘッダ優先
        if (StringUtils.hasText(authorization) && authorization.startsWith("Basic ")) {
            String b64 = authorization.substring(6);
            String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            String email = (idx >= 0) ? decoded.substring(0, idx) : decoded;
            String pass = (idx >= 0) ? decoded.substring(idx + 1) : "";
            if (StringUtils.hasText(email) && StringUtils.hasText(pass)) {
                return new Cred(email, pass);
            }
        }
        // JSON ボディ
        if (body != null && StringUtils.hasText(body.email) && StringUtils.hasText(body.password)) {
            return new Cred(body.email, body.password);
        }
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "email and password are required");
    }

    /** JSON 入力用の最小DTO */
    public static class LoginRequest {
        public final String email;
        public final String password;

        @JsonCreator
        public LoginRequest(
                @JsonProperty("email") String email,
                @JsonProperty("password") String password) {
            this.email = email;
            this.password = password;
        }
    }

    private record Cred(String email, String password) { }
}

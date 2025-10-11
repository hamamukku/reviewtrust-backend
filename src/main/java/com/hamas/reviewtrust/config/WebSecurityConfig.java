// WebSecurityConfig.java (placeholder)
package com.hamas.reviewtrust.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 管理APIの保護と CORS 分離。固定1ユーザーの最小構成（MVP）。 
 * - 管理API: /api/admin/** は ADMIN 権限を要求（/api/admin/login は許可）
 * - 公開API: /api/** は許可（読み取り主体）
 * - Swagger と /actuator/health は許可
 * - CSRFはAPI前提のため無効化、セッションはSTATELESS、Basic認証を暫定採用
 *   （将来は AuthController によるトークン化へ差し替え） 
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 固定1ユーザー（環境変数 admin.email / admin.password からバインド） */
    @Bean
    public InMemoryUserDetailsManager userDetailsService(
            PasswordEncoder encoder,
            PropertiesConfig.Admin adminProps
    ) {
        String rawOrBcrypt = adminProps.getPassword();
        String usable = isBcrypt(rawOrBcrypt) ? rawOrBcrypt : encoder.encode(rawOrBcrypt);

        UserDetails admin = User.withUsername(adminProps.getEmail())
                .password(usable)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    private boolean isBcrypt(String value) {
        return value != null && value.startsWith("$2");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PropertiesConfig.Admin adminProps
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> { }) // CorsConfigurationSource bean を使用
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ドキュメント/ヘルス
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/actuator/health").permitAll()
                // 管理ログイン入口だけ明示許可
                .requestMatchers(HttpMethod.POST, adminProps.getLoginPath()).permitAll()
                // 管理系と Actuator（health 以外）は ADMIN
                .requestMatchers("/api/admin/**", "/actuator/**").hasRole("ADMIN")
                // 公開API（読み取り系）
                .requestMatchers("/api/**").permitAll()
                // その他は拒否
                .anyRequest().denyAll()
            )
            // MVPでは簡易にBasic認証。将来はJWT/セッショントークンへ置換。
            .httpBasic(httpBasic -> {});
        return http.build();
    }

    /** パス毎に CORS を分離（公開と管理）。 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            PropertiesConfig.CorsPublic publicCors,
            PropertiesConfig.CorsAdmin adminCors
    ) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        CorsConfiguration pub = toCors(publicCors);
        source.registerCorsConfiguration("/api/**", pub);
        // ドキュメントも公開側CORSに準拠
        source.registerCorsConfiguration("/v3/api-docs/**", pub);
        source.registerCorsConfiguration("/swagger-ui/**", pub);

        CorsConfiguration adm = toCors(adminCors);
        source.registerCorsConfiguration("/api/admin/**", adm);

        return source;
    }

    private CorsConfiguration toCors(PropertiesConfig.CorsPublic p) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(p.getOrigins());
        c.setAllowedMethods(p.getMethods());
        c.setAllowedHeaders(p.getHeaders());
        c.setAllowCredentials(p.isAllowCredentials());
        c.setExposedHeaders(List.of("Location", "Link"));
        c.setMaxAge(3600L);
        return c;
    }

    private CorsConfiguration toCors(PropertiesConfig.CorsAdmin p) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(p.getOrigins());
        c.setAllowedMethods(p.getMethods());
        c.setAllowedHeaders(p.getHeaders());
        c.setAllowCredentials(p.isAllowCredentials());
        c.setExposedHeaders(List.of("Location", "Link"));
        c.setMaxAge(3600L);
        return c;
    }
}

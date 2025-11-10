package com.hamas.reviewtrust.config;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * JWT の発行/検証を一元化。
 * - HS256 固定（alg の統一）
 * - secret は SecurityConfig で raw UTF-8 を HmacSHA256 鍵化している前提
 * - roles / authorities / scope の相互運用（発行は roles 固定）
 */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    @Value("${security.jwt.ttlSeconds:3600}")
    private long ttlSeconds;

    public JwtTokenService(JwtEncoder encoder, JwtDecoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    /** 管理用トークンを発行（roles は ROLE_ 付きで渡す） */
    public String create(String subject, Collection<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(exp)
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtEncoderParameters params = JwtEncoderParameters.from(header, claims);

        return encoder.encode(params).getTokenValue();
    }

    /** 署名・有効期限を検証しペイロードを返す */
    public Jwt verify(String token) {
        return decoder.decode(token);
    }

    /** 認可ヘルパ：claims から権限配列を抽出（roles / authorities / scope の順で探索） */
    @SuppressWarnings("unchecked")
    public static List<String> extractAuthorities(Jwt jwt) {
        Object roles = firstNonNull(jwt.getClaims().get("roles"),
                                    jwt.getClaims().get("authorities"),
                                    jwt.getClaims().get("scope"));
        if (roles == null) return List.of();
        if (roles instanceof String s) return List.of(s.split("[ ,]"));
        if (roles instanceof Collection<?> col) return col.stream().map(Object::toString).toList();
        return List.of();
    }

    private static Object firstNonNull(Object... objs){
        for(Object o : objs) if (o != null) return o;
        return null;
    }
}

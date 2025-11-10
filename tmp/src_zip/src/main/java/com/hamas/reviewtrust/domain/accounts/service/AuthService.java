// AuthService.java (placeholder)

// src/main/java/com/hamas/reviewtrust/domain/accounts/service/AuthService.java
package com.hamas.reviewtrust.domain.accounts.service;

import com.hamas.reviewtrust.domain.accounts.entity.AdminUser;
import com.hamas.reviewtrust.domain.accounts.repo.AdminUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 管理者認証用サービス。
 * 依存ライブラリ不要の PBKDF2(SHA-256) を内蔵し、ハッシュ形式は
 *   "pbkdf2:sha256:{iterations}${base64(salt)}${base64(hash)}"
 * として保存・検証する。
 */
@Service
public class AuthService {

    private final AdminUserRepository repo;
    private final PasswordHasher hasher = new PasswordHasher();

    public AuthService(AdminUserRepository repo) {
        this.repo = repo;
    }

    /** 管理者ユーザ登録（存在すれば例外）。戻り値は作成ユーザID。 */
    @Transactional
    public UUID registerAdmin(String username, String rawPassword) {
        validateUsername(username);
        validatePassword(rawPassword);
        if (repo.findByUsername(username).isPresent()) {
            throw new IllegalStateException("username already exists: " + username);
        }
        String hash = hasher.hash(rawPassword);
        AdminUser u = AdminUser.newActive(username, hash);
        repo.save(u);
        return u.getId();
    }

    /** 既存ユーザのパスワード変更（旧パス確認あり）。 */
    @Transactional
    public void changePassword(UUID adminId, String oldRaw, String newRaw) {
        AdminUser u = repo.findById(adminId).orElseThrow(() -> new IllegalArgumentException("admin not found"));
        if (!hasher.verify(oldRaw, u.getPasswordHash())) {
            throw new IllegalArgumentException("old password mismatch");
        }
        validatePassword(newRaw);
        u.setPasswordHash(hasher.hash(newRaw));
        // updatedAt は @PreUpdate で更新
    }

    /** 認証：成功時に AdminUser を返す（enabled=false は拒否）。 */
    @Transactional(readOnly = true)
    public AdminUser authenticate(String username, String rawPassword) {
        AdminUser u = repo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("username not found"));
        if (!u.isEnabled()) {
            throw new IllegalStateException("account disabled");
        }
        if (!hasher.verify(rawPassword, u.getPasswordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return u;
    }

    /** 便宜メソッド：ユーザ取得 */
    @Transactional(readOnly = true)
    public Optional<AdminUser> findByUsername(String username) {
        return repo.findByUsername(username);
    }

    private static void validateUsername(String username) {
        if (username == null || !username.matches("^[A-Za-z0-9_\\-]{3,32}$")) {
            throw new IllegalArgumentException("invalid username (3..32 alnum/_/-)");
        }
    }
    private static void validatePassword(String pw) {
        if (pw == null || pw.length() < 8) {
            throw new IllegalArgumentException("password too short (>=8)");
        }
    }

    /**
     * 依存なし PBKDF2 ハッシャ
     */
    static final class PasswordHasher {
        private static final String ALG = "PBKDF2WithHmacSHA256";
        private static final int ITER = 185_000;
        private static final int SALT_LEN = 16;
        private static final int KEY_LEN_BITS = 256;
        private final SecureRandom random = new SecureRandom();

        String hash(String raw) {
            try {
                byte[] salt = new byte[SALT_LEN];
                random.nextBytes(salt);
                byte[] dk = derive(raw.toCharArray(), salt, ITER, KEY_LEN_BITS);
                return "pbkdf2:sha256:%d$%s$%s".formatted(
                        ITER,
                        Base64.getEncoder().withoutPadding().encodeToString(salt),
                        Base64.getEncoder().withoutPadding().encodeToString(dk)
                );
            } catch (Exception e) {
                throw new RuntimeException("hash error", e);
            }
        }

        boolean verify(String raw, String stored) {
            try {
                // 形式: pbkdf2:sha256:{iter}${salt}${hash}
                String[] parts = stored.split("\\$");
                if (parts.length != 3) return false;
                String[] head = parts[0].split(":");
                if (head.length != 3) return false;
                int iter = Integer.parseInt(head[2]);
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expect = Base64.getDecoder().decode(parts[2]);
                byte[] actual = derive(raw.toCharArray(), salt, iter, expect.length * 8);
                return constantTimeEq(expect, actual);
            } catch (Exception e) {
                return false;
            }
        }

        private static byte[] derive(char[] pw, byte[] salt, int iter, int bits) throws Exception {
            KeySpec spec = new PBEKeySpec(pw, salt, iter, bits);
            SecretKeyFactory f = SecretKeyFactory.getInstance(ALG);
            return f.generateSecret(spec).getEncoded();
        }
        private static boolean constantTimeEq(byte[] a, byte[] b) {
            if (a.length != b.length) return false;
            int res = 0;
            for (int i = 0; i < a.length; i++) res |= a[i] ^ b[i];
            return res == 0;
        }
    }
}

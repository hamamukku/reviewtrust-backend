package com.hamas.reviewtrust.domain.accounts.service;

import com.hamas.reviewtrust.domain.accounts.entity.AdminUser;
import com.hamas.reviewtrust.domain.accounts.repo.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@Service
public class AuthService {

    private final AdminUserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final LegacyPasswordHasher legacyHasher = new LegacyPasswordHasher();

    public AuthService(AdminUserRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UUID registerAdmin(String username, String rawPassword) {
        validateUsername(username);
        validatePassword(rawPassword);
        if (repo.findByUsername(username).isPresent()) {
            throw new IllegalStateException("username already exists: " + username);
        }
        String hash = passwordEncoder.encode(rawPassword);
        AdminUser user = AdminUser.newActive(username, hash);
        user.setEmail(username + "@example.com");
        repo.save(user);
        return user.getId();
    }

    @Transactional
    public void changePassword(UUID adminId, String oldRaw, String newRaw) {
        AdminUser user = repo.findById(adminId).orElseThrow(() -> new IllegalArgumentException("admin not found"));
        if (!matchesPassword(oldRaw, user.getPasswordHash())) {
            throw new IllegalArgumentException("old password mismatch");
        }
        validatePassword(newRaw);
        user.setPasswordHash(passwordEncoder.encode(newRaw));
    }

    @Transactional
    public AdminUser authenticate(String usernameOrEmail, String rawPassword) {
        AdminUser user = repo.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new IllegalArgumentException("username/email not found"));

        if (!user.isEnabled()) {
            throw new IllegalStateException("account disabled");
        }

        if (!verifyAndUpgradePassword(rawPassword, user)) {
            throw new IllegalArgumentException("invalid credentials");
        }

        return user;
    }

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

    private boolean verifyAndUpgradePassword(String raw, AdminUser user) {
        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank()) return false;

        // ✅ 現在のパスワード方式（BCrypt）で検証成功
        if (passwordEncoder.matches(raw, stored)) return true;

        // ✅ 古いハッシュ（PBKDF2）だったが一致した場合は再ハッシュして保存
        if (legacyHasher.isLegacyFormat(stored) && legacyHasher.verify(raw, stored)) {
            user.setPasswordHash(passwordEncoder.encode(raw));
            return true;
        }

        return false;
    }

    private boolean matchesPassword(String raw, String stored) {
        if (stored == null || stored.isBlank()) return false;
        return passwordEncoder.matches(raw, stored)
                || (legacyHasher.isLegacyFormat(stored) && legacyHasher.verify(raw, stored));
    }

    /**
     * PBKDF2 legacy hash verifier (for backward compatibility).
     */
    static final class LegacyPasswordHasher {
        private static final String ALG = "PBKDF2WithHmacSHA256";

        boolean isLegacyFormat(String stored) {
            return stored != null && stored.startsWith("pbkdf2:sha256:");
        }

        boolean verify(String raw, String stored) {
            try {
                String[] parts = stored.split("\\$");
                if (parts.length != 3) return false;
                String[] head = parts[0].split(":");
                if (head.length != 3) return false;
                int iter = Integer.parseInt(head[2]);
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expected = Base64.getDecoder().decode(parts[2]);
                byte[] actual = derive(raw.toCharArray(), salt, iter, expected.length * 8);
                return constantTimeEq(expected, actual);
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

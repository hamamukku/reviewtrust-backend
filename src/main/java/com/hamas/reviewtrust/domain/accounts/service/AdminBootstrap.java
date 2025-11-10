package com.hamas.reviewtrust.domain.accounts.service;

import com.hamas.reviewtrust.domain.accounts.entity.AdminUser;
import com.hamas.reviewtrust.domain.accounts.repo.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.Optional;

/**
 * 起動時の管理者アカウント自動作成。
 *
 * application.yml 例:
 * admin:
 *   bootstrap:
 *     enabled: true
 *     username: admin
 *     password: changeme123
 *
 * 環境変数でも上書き可能:
 *  ADMIN_BOOTSTRAP_ENABLED, ADMIN_BOOTSTRAP_USERNAME, ADMIN_BOOTSTRAP_PASSWORD
 */
@Component
public class AdminBootstrap {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthService authService;
    private final AdminUserRepository repo;

    @Value("${admin.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${admin.bootstrap.username:admin}")
    private String username;

    @Value("${admin.bootstrap.password:}")
    private String password;

    public AdminBootstrap(AuthService authService, AdminUserRepository repo) {
        this.authService = authService;
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onReady() {
        if (!enabled) {
            log.info("[AdminBootstrap] disabled -> skip");
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("[AdminBootstrap] password is empty -> skip for safety");
            return;
        }
        Optional<AdminUser> existing = repo.findByUsername(username);
        if (existing.isPresent()) {
            log.info("[AdminBootstrap] admin '{}' already exists -> skip", username);
            return;
        }
        authService.registerAdmin(username, password);
        log.info("[AdminBootstrap] admin '{}' created", username);
    }
}

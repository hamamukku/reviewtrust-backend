package com.hamas.reviewtrust.security;

import com.hamas.reviewtrust.domain.accounts.entity.AdminUser;
import com.hamas.reviewtrust.domain.accounts.repo.AdminUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository repo;

    public AdminUserDetailsService(AdminUserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String input) throws UsernameNotFoundException {
        AdminUser user = repo.findByUsernameOrEmail(input, input)
                .orElseThrow(() -> new UsernameNotFoundException("Admin user not found: " + input));

        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("Account is disabled: " + input);
        }

        return mapToUserDetails(user);
    }

    private UserDetails mapToUserDetails(AdminUser source) {
        String username = resolvePrincipal(source);
        String passwordHash = source.getPasswordHash();
        String rolesCsv = normalizeRoles(source.getRoles());

        List<GrantedAuthority> authorities = AuthorityUtils.commaSeparatedStringToAuthorityList(rolesCsv);

        return User.withUsername(username)
                .password(passwordHash)
                .authorities(authorities)
                .accountLocked(!source.isEnabled())
                .disabled(!source.isEnabled())
                .build();
    }

    private static String resolvePrincipal(AdminUser source) {
        if (StringUtils.hasText(source.getUsername())) return source.getUsername();
        if (StringUtils.hasText(source.getEmail())) return source.getEmail();
        return source.getId().toString();
    }

    private static String normalizeRoles(String raw) {
        if (!StringUtils.hasText(raw)) return "ROLE_ADMIN";

        String cleaned = raw.trim();
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        cleaned = cleaned.replaceAll("[\\s;]+", ",");

        StringBuilder result = new StringBuilder();
        for (String token : cleaned.split(",")) {
            String role = token.trim();
            if (role.isEmpty()) continue;
            if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
            if (result.length() > 0) result.append(",");
            result.append(role);
        }

        return result.length() == 0 ? "ROLE_ADMIN" : result.toString();
    }
}

package com.hamas.reviewtrust.security;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbc;

    public AdminUserDetailsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // email/username どちらでもヒット
        var sql = """
            SELECT COALESCE(username, email) AS uname,
                   email,
                   password_hash,
                   enabled,
                   roles
              FROM public.admin_users
             WHERE email = ? OR username = ?
             LIMIT 1
        """;
        try {
            return jdbc.queryForObject(sql, (rs, i) -> {
                String uname = rs.getString("uname");
                String pw    = rs.getString("password_hash"); // {bcrypt}... 形式
                boolean enabled = rs.getBoolean("enabled");
                String rolesRaw = rs.getString("roles");       // 例: 'ROLE_ADMIN' / 'ADMIN' / '{ROLE_ADMIN}'
                String rolesCsv = normalizeRoles(rolesRaw);
                List<GrantedAuthority> auths = AuthorityUtils.commaSeparatedStringToAuthorityList(rolesCsv);
                // enabled=false はアカウントロック扱いに寄せる
                return User.withUsername(uname)
                        .password(pw)
                        .authorities(auths)
                        .accountLocked(!enabled)
                        .build();
            }, username, username);
        } catch (EmptyResultDataAccessException e) {
            throw new UsernameNotFoundException("admin user not found: " + username);
        }
    }

    private static String normalizeRoles(String raw) {
        if (raw == null || raw.isBlank()) return "ROLE_ADMIN";
        String s = raw.trim();
        // Postgres配列っぽい {ROLE_ADMIN} を剥がす
        if (s.startsWith("{") && s.endsWith("}")) s = s.substring(1, s.length()-1);
        // スペース/カンマ仕切りをカンマに正規化
        s = s.replaceAll("[\\s;]+", ",");
        // 各要素に ROLE_ プレフィックスを付けていなければ付与
        StringBuilder out = new StringBuilder();
        for (String token : s.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (!t.startsWith("ROLE_")) t = "ROLE_" + t;
            if (out.length() > 0) out.append(',');
            out.append(t);
        }
        return out.length() == 0 ? "ROLE_ADMIN" : out.toString();
    }
}

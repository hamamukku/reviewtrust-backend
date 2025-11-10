// PropertiesConfig.java (placeholder)
package com.hamas.reviewtrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 環境依存のプロパティ集約。application.yml / 環境変数からバインド。
 * - admin.*     : 管理ログイン、ドキュメント公開等の方針
 * - cors.public : 公開APIのCORS
 * - cors.admin  : 管理APIのCORS
 *
 * 例（application.yml）:
 * admin:
 *   email: ${ADMIN_EMAIL:admin@example.com}
 *   password: ${ADMIN_PASSWORD:change-me}
 *   login-path: /api/admin/login
 * cors:
 *   public:
 *     origins: ["http://localhost:5173"]
 *   admin:
 *     origins: ["http://localhost:5173"]
 */
@Configuration
@EnableConfigurationProperties({
        PropertiesConfig.Admin.class,
        PropertiesConfig.CorsPublic.class,
        PropertiesConfig.CorsAdmin.class
})
public class PropertiesConfig {

    @ConfigurationProperties(prefix = "admin")
    public static class Admin {
        /** 管理ユーザーのログインID（メールアドレス想定） */
        private String email = "admin@example.com";
        /** 平文 or BCrypt（$2...）。平文なら起動時にBCryptへ自動変換して使用。 */
        private String password = "change-me";
        /** 管理ログインAPIのパス（デフォルトは /api/admin/login） */
        private String loginPath = "/api/admin/login";
        /** devでSwaggerを開けるか（Security側での利用を想定、ここでは保持のみ） */
        private boolean swagger = true;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getLoginPath() { return loginPath; }
        public void setLoginPath(String loginPath) { this.loginPath = loginPath; }

        public boolean isSwagger() { return swagger; }
        public void setSwagger(boolean swagger) { this.swagger = swagger; }
    }

    /** 公開APIのCORS設定（/api/**, Swagger等） */
    @ConfigurationProperties(prefix = "cors.public")
    public static class CorsPublic {
        private List<String> origins = new ArrayList<>(List.of("http://localhost:5173"));
        private List<String> methods = new ArrayList<>(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        private List<String> headers = new ArrayList<>(List.of("*"));
        private boolean allowCredentials = true;

        public List<String> getOrigins() { return origins; }
        public void setOrigins(List<String> origins) { this.origins = origins; }
        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }
        public List<String> getHeaders() { return headers; }
        public void setHeaders(List<String> headers) { this.headers = headers; }
        public boolean isAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }
    }

    /** 管理APIのCORS設定（/api/admin/**） */
    @ConfigurationProperties(prefix = "cors.admin")
    public static class CorsAdmin {
        private List<String> origins = new ArrayList<>(List.of("http://localhost:5173"));
        private List<String> methods = new ArrayList<>(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        private List<String> headers = new ArrayList<>(List.of("*"));
        private boolean allowCredentials = true;

        public List<String> getOrigins() { return origins; }
        public void setOrigins(List<String> origins) { this.origins = origins; }
        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }
        public List<String> getHeaders() { return headers; }
        public void setHeaders(List<String> headers) { this.headers = headers; }
        public boolean isAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }
    }
}


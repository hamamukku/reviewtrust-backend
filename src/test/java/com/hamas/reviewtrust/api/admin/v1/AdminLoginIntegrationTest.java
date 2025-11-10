package com.hamas.reviewtrust.api.admin.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.accounts.entity.AdminUser;
import com.hamas.reviewtrust.domain.accounts.repo.AdminUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admintest;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=false",
        "security.jwt.secret=0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
        "logging.level.org.springframework.security=INFO"
})
class AdminLoginIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AdminUserRepository adminUserRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adminUserRepository.deleteAll();
        AdminUser user = AdminUser.newActive("admin", passwordEncoder.encode("admin123"));
        user.setEmail("admin@example.com");
        user.setRoles("ROLE_ADMIN");
        adminUserRepository.save(user);
    }

    @Test
    void loginWithUsernameSucceeds() throws Exception {
        LoginRequest request = new LoginRequest("admin", null, "admin123");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.sub").value("admin"));
    }

    @Test
    void loginWithEmailSucceeds() throws Exception {
        LoginRequest request = new LoginRequest(null, "admin@example.com", "admin123");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.sub").value("admin"));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        LoginRequest request = new LoginRequest("admin", null, "wrong");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E_CREDENTIALS"));
    }

    private record LoginRequest(String username, String email, String password) {}
}

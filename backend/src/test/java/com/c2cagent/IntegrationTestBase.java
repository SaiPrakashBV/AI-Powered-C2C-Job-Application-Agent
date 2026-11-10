package com.c2cagent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.c2cagent.auth.AuthDtos;
import com.c2cagent.auth.AuthService;
import com.c2cagent.auth.CurrentUser;
import com.c2cagent.users.AppUser;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration tests run against a real PostgreSQL with the real Flyway migrations.
 * <ul>
 *   <li>Default: a Testcontainers PostgreSQL (requires Docker).</li>
 *   <li>Without Docker: set C2C_TEST_DB_URL / C2C_TEST_DB_USER / C2C_TEST_DB_PASSWORD to a disposable database.
 *       <b>All tables in it are truncated between tests.</b></li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {
    public static final String PASSWORD = "Correct-Horse-42";
    private static PostgreSQLContainer container;

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected AuthService authService;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("C2C_TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("C2C_TEST_DB_USER", "c2c"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("C2C_TEST_DB_PASSWORD", ""));
        } else {
            synchronized (IntegrationTestBase.class) {
                if (container == null) {
                    container = new PostgreSQLContainer("postgres:16-alpine");
                    container.start();
                }
            }
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        String dataDir = Files.createTempDirectory("c2c-test-data").toString();
        registry.add("app.data-dir", () -> dataDir);
        registry.add("app.jobs.worker-enabled", () -> "false"); // tests drive the worker explicitly
        registry.add("app.gmail.scheduler-enabled", () -> "false");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE email_attachment, incoming_email, email_source_rule, gmail_sync_checkpoint, gmail_account, "
                + "background_job, audit_event, workspace_settings, app_user, workspace CASCADE");
    }

    protected AppUser createOwner(String username) {
        return authService.setup(new AuthDtos.SetupRequest(username, "Test " + username, null, PASSWORD, null));
    }

    /** Authenticates a MockMvc request as the given user (bypasses the login endpoint). */
    protected static RequestPostProcessor as(AppUser u) {
        CurrentUser principal = new CurrentUser(u.getId(), u.getWorkspaceId(), u.getUsername(), u.getDisplayName(),
                u.getRole());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole()))));
    }
}

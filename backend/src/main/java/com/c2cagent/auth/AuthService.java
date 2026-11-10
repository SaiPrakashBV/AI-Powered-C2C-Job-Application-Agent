package com.c2cagent.auth;

import com.c2cagent.audit.AuditService;
import com.c2cagent.common.ApiException;
import com.c2cagent.configuration.AppProperties;
import com.c2cagent.users.AppUser;
import com.c2cagent.users.AppUserRepository;
import com.c2cagent.users.SettingsService;
import com.c2cagent.users.UserRole;
import com.c2cagent.users.Workspace;
import com.c2cagent.users.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    /** Arbitrary constant key for the PostgreSQL advisory lock that serializes first-time setup. */
    private static final long SETUP_LOCK_KEY = 0x0C2C_0001L;
    /** Pre-computed hash so failed lookups take similar time to real password checks. */
    private final String dummyHash;

    private final AppUserRepository users;
    private final WorkspaceRepository workspaces;
    private final SettingsService settings;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final Clock clock;

    public AuthService(AppUserRepository users, WorkspaceRepository workspaces, SettingsService settings,
                       PasswordEncoder encoder, AuditService audit, JdbcTemplate jdbc, AppProperties props,
                       Optional<Clock> clock) {
        this.users = users;
        this.workspaces = workspaces;
        this.settings = settings;
        this.encoder = encoder;
        this.audit = audit;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock.orElse(Clock.systemUTC());
        this.dummyHash = encoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional(readOnly = true)
    public boolean isSetupRequired() {
        return users.count() == 0;
    }

    /** First-time installation: creates the single local owner and their workspace. Only allowed once. */
    @Transactional
    public AppUser setup(AuthDtos.SetupRequest req) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)::text", String.class, SETUP_LOCK_KEY);
        if (users.count() > 0) {
            throw ApiException.conflict("SETUP_ALREADY_COMPLETED", "Initial setup has already been completed");
        }
        validatePasswordStrength(req.password(), req.username());
        String wsName = req.workspaceName() == null || req.workspaceName().isBlank()
                ? req.displayName() + "'s workspace" : req.workspaceName().strip();
        Workspace ws = workspaces.save(new Workspace(wsName));
        AppUser user = users.save(new AppUser(ws.getId(), req.username().strip(), req.displayName().strip(),
                blankToNull(req.email()), encoder.encode(req.password()), UserRole.OWNER));
        settings.getOrCreate(ws.getId());
        audit.recordSystem(ws.getId(), "SETUP_COMPLETED", "app_user", user.getId().toString(),
                "First-time setup completed; owner account created", Map.of("username", user.getUsername()));
        return user;
    }

    /**
     * Verifies credentials with lockout after repeated failures. Error messages are deliberately generic so
     * they do not reveal whether a username exists.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AppUser authenticate(String username, String password) {
        Instant now = clock.instant();
        AppUser user = users.findByUsernameIgnoreCase(username.strip()).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            audit.recordAnonymous(null, "LOGIN_FAILED", "Login failed for unknown user", Map.of());
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw invalidCredentials();
        }
        if (user.isLockedAt(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again after " + user.getLockedUntil() + ".");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(props.security().maxFailedLogins(), now.plus(props.security().lockoutDuration()));
            users.saveAndFlush(user);
            audit.recordAnonymous(user.getWorkspaceId(), "LOGIN_FAILED", "Login failed (wrong password)",
                    Map.of("username", user.getUsername(), "locked", user.isLockedAt(now)));
            throw invalidCredentials();
        }
        if (encoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(encoder.encode(password));
        }
        user.recordSuccessfulLogin(now);
        users.saveAndFlush(user);
        audit.recordSystem(user.getWorkspaceId(), "LOGIN_SUCCEEDED", "app_user", user.getId().toString(),
                "User logged in", Map.of("username", user.getUsername()));
        return user;
    }

    @Transactional
    public void changePassword(CurrentUser current, String currentPassword, String newPassword) {
        AppUser user = users.findById(current.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        validatePasswordStrength(newPassword, user.getUsername());
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        audit.recordUser(current, "PASSWORD_CHANGED", "app_user", user.getId().toString(), "Password changed",
                Map.of());
    }

    void validatePasswordStrength(String password, String username) {
        int min = props.security().minPasswordLength();
        if (password == null || password.length() < min) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be at least " + min + " characters");
        }
        if (password.toLowerCase().contains(username.strip().toLowerCase())) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must not contain the username");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigitOrSymbol = password.chars().anyMatch(c -> !Character.isLetter(c));
        if (!hasLetter || !hasDigitOrSymbol) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Password must contain letters and at least one digit or symbol");
        }
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}

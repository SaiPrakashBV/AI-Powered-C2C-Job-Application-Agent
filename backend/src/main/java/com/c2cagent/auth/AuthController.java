package com.c2cagent.auth;

import com.c2cagent.audit.AuditService;
import com.c2cagent.configuration.AppProperties;
import com.c2cagent.users.AppUser;
import com.c2cagent.users.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;
    private final SettingsService settingsService;
    private final SecurityContextRepository contextRepository;
    private final AuditService audit;
    private final AppProperties props;

    public AuthController(AuthService authService, SettingsService settingsService,
                          SecurityContextRepository contextRepository, AuditService audit, AppProperties props) {
        this.authService = authService;
        this.settingsService = settingsService;
        this.contextRepository = contextRepository;
        this.audit = audit;
        this.props = props;
    }

    @GetMapping("/setup-status")
    public AuthDtos.SetupStatus setupStatus() {
        return new AuthDtos.SetupStatus(authService.isSetupRequired(), props.demoMode());
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue the XSRF-TOKEN cookie (call once before the first POST)")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    @PostMapping("/setup")
    @Operation(summary = "First-time setup: create the local owner account (only when no user exists)")
    public ResponseEntity<AuthDtos.MeResponse> setup(@Valid @RequestBody AuthDtos.SetupRequest req,
                                                     HttpServletRequest request, HttpServletResponse response) {
        AppUser user = authService.setup(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(startSession(user, request, response));
    }

    @PostMapping("/login")
    public AuthDtos.MeResponse login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest request,
                                     HttpServletResponse response) {
        AppUser user = authService.authenticate(req.username(), req.password());
        return startSession(user, request, response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out / lock the application (invalidates the session)")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            audit.recordUser(user, "LOGOUT", "app_user", user.userId().toString(), "User logged out", Map.of());
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(CurrentUser user) {
        int timeout = settingsService.getOrCreate(user.workspaceId()).getSessionTimeoutMinutes();
        return new AuthDtos.MeResponse(user.userId(), user.workspaceId(), user.username(), user.displayName(),
                user.role(), timeout);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(CurrentUser user,
                                               @Valid @RequestBody AuthDtos.ChangePasswordRequest req) {
        authService.changePassword(user, req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    private AuthDtos.MeResponse startSession(AppUser user, HttpServletRequest request,
                                             HttpServletResponse response) {
        CurrentUser principal = new CurrentUser(user.getId(), user.getWorkspaceId(), user.getUsername(),
                user.getDisplayName(), user.getRole());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        // Prevent session fixation: rotate the session id before storing the authenticated context.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        int timeout = settingsService.getOrCreate(user.getWorkspaceId()).getSessionTimeoutMinutes();
        request.getSession(true).setMaxInactiveInterval(timeout * 60);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return new AuthDtos.MeResponse(user.getId(), user.getWorkspaceId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), timeout);
    }
}

package com.c2cagent.auth;

import com.c2cagent.users.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record SetupStatus(boolean setupRequired, boolean demoMode) {}

    public record SetupRequest(
            @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "[A-Za-z0-9._@-]+",
                    message = "may contain letters, digits and . _ @ - only") String username,
            @NotBlank @Size(max = 200) String displayName,
            @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String password,
            @Size(max = 200) String workspaceName) {}

    public record LoginRequest(@NotBlank @Size(max = 100) String username,
                               @NotBlank @Size(max = 200) String password) {}

    public record MeResponse(UUID userId, UUID workspaceId, String username, String displayName, UserRole role,
                             int sessionTimeoutMinutes) {}

    public record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
                                        @NotBlank @Size(max = 200) String newPassword) {}
}

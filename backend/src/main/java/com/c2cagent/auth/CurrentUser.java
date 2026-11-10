package com.c2cagent.auth;

import com.c2cagent.users.UserRole;
import java.io.Serializable;
import java.util.UUID;

/**
 * The authenticated principal stored in the session. Controllers receive it as a method argument; every
 * workspace-owned query must filter by {@link #workspaceId()} to keep data isolated between users.
 */
public record CurrentUser(UUID userId, UUID workspaceId, String username, String displayName, UserRole role)
        implements Serializable {}

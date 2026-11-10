package com.burgerlab.auth.dto;

import com.burgerlab.auth.UserRole;

public record AuthUserResponse(
        long id,
        String username,
        String displayName,
        UserRole role
) {
}

package com.burgerlab.auth;

import com.burgerlab.auth.dto.AuthUserResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public AuthUserResponse toResponse(AppUserPrincipal principal) {
        return new AuthUserResponse(
                principal.id(),
                principal.username(),
                principal.displayName(),
                principal.role()
        );
    }
}

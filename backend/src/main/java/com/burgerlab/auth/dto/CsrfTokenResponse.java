package com.burgerlab.auth.dto;

public record CsrfTokenResponse(
        String token,
        String headerName,
        String parameterName
) {
}

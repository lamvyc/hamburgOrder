package com.burgerlab.common.error;

/**
 * A feature-level lookup failure that is safe to expose as a client error.
 * Internal exception details, SQL and identifiers not meant for the client
 * stay out of the public response.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String code;

    public ResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

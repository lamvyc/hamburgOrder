package com.burgerlab.auth;

public enum UserRole {
    CUSTOMER,
    STAFF,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}

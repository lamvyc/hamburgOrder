package com.burgerlab.store;

public enum StoreBusinessStatus {
    OPEN,
    CLOSED;

    public static StoreBusinessStatus fromDatabase(String value) {
        try {
            return StoreBusinessStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Unknown store business status: " + value, exception);
        }
    }
}

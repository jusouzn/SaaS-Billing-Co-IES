package com.saas.billing;

import java.util.Objects;

public record Customer(String email, String status) {

    public Customer {
        Objects.requireNonNull(email);
        Objects.requireNonNull(status);
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}

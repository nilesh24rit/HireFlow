package com.hireflow.notification.recipient;

import java.util.UUID;

public record Recipient(UUID userId, String email, String displayName) {

    public Recipient {
        if (userId == null || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Recipient identity and email are required");
        }
    }
}

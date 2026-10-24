package com.hireflow.notification.recipient;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Safe default until authenticated service-to-service directory clients are configured.
 */
@Component
public class UnavailableRecipientDirectory implements RecipientDirectory {

    @Override
    public Optional<Recipient> findByUserId(UUID userId) {
        return Optional.empty();
    }
}

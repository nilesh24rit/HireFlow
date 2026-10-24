package com.hireflow.notification.recipient;

import java.util.Optional;
import java.util.UUID;

public interface RecipientDirectory {

    Optional<Recipient> findByUserId(UUID userId);
}

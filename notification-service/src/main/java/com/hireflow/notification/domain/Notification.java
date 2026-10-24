package com.hireflow.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID eventId,
        UUID recipientUserId,
        NotificationType notificationType,
        String subject,
        String message,
        String channel,
        DeliveryStatus deliveryStatus,
        int attemptCount,
        Instant createdAt,
        Instant sentAt,
        Instant nextAttemptAt,
        String lastError,
        boolean read) {
}

package com.hireflow.notification.api;

import java.time.Instant;
import java.util.UUID;

import com.hireflow.notification.domain.DeliveryStatus;
import com.hireflow.notification.domain.Notification;
import com.hireflow.notification.domain.NotificationType;

public record NotificationResponse(
        UUID id,
        NotificationType notificationType,
        String subject,
        String message,
        DeliveryStatus deliveryStatus,
        boolean read,
        Instant createdAt,
        Instant sentAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.id(), notification.notificationType(),
                notification.subject(), notification.message(), notification.deliveryStatus(),
                notification.read(), notification.createdAt(), notification.sentAt());
    }
}

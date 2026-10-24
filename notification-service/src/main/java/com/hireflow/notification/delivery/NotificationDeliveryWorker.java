package com.hireflow.notification.delivery;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hireflow.notification.domain.Notification;
import com.hireflow.notification.recipient.RecipientDirectory;
import com.hireflow.notification.repository.NotificationRepository;

@Component
public class NotificationDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryWorker.class);
    private static final int MAX_ATTEMPTS = 5;
    private final NotificationRepository repository;
    private final RecipientDirectory directory;
    private final NotificationMailSender mailSender;

    public NotificationDeliveryWorker(NotificationRepository repository,
            RecipientDirectory directory, NotificationMailSender mailSender) {
        this.repository = repository;
        this.directory = directory;
        this.mailSender = mailSender;
    }

    @Scheduled(fixedDelayString = "${hireflow.notifications.delivery-poll:PT10S}")
    public void deliverPending() {
        repository.recoverAbandoned(Instant.now().minus(Duration.ofMinutes(10)));
        for (Notification notification : repository.findPending(50)) {
            if (repository.claim(notification.id()) != 1) {
                continue;
            }
            try {
                var recipient = directory.findByUserId(notification.recipientUserId())
                        .orElseThrow(() -> new IllegalArgumentException("Recipient contact unavailable"));
                mailSender.send(notification, recipient);
                repository.markSent(notification.id());
            } catch (IllegalArgumentException ex) {
                repository.markFailed(notification.id(), sanitize(ex), null, true);
            } catch (RuntimeException ex) {
                Instant retryAt = notification.attemptCount() + 1 >= MAX_ATTEMPTS
                        ? null
                        : Instant.now().plus(Duration.ofSeconds(1L << Math.min(notification.attemptCount(), 6)));
                repository.markFailed(notification.id(), sanitize(ex), retryAt,
                        notification.attemptCount() + 1 >= MAX_ATTEMPTS);
                log.warn("Notification delivery failed id={} attempt={}",
                        notification.id(), notification.attemptCount() + 1);
            }
        }
    }

    private static String sanitize(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName()
                : message.replaceAll("[\\r\\n]", " ").substring(0, Math.min(1000, message.length()));
    }
}

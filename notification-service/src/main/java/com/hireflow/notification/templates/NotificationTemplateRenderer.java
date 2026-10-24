package com.hireflow.notification.templates;

import org.springframework.stereotype.Component;

import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.notification.domain.NotificationType;

@Component
public class NotificationTemplateRenderer {

    public NotificationTemplate render(ApplicationEvent event, NotificationType type) {
        var payload = event.payload();
        String reference = payload.applicationId().toString();
        String job = payload.jobTitle() == null ? "" : " for " + plain(payload.jobTitle());
        return switch (type) {
            case APPLICATION_SUBMITTED_CANDIDATE -> new NotificationTemplate(
                    "Application submitted",
                    "Your application " + reference + " was submitted" + job + ".");
            case APPLICATION_SUBMITTED_RECRUITER -> new NotificationTemplate(
                    "New application received",
                    "A new application " + reference + " was submitted" + job + ".");
            case APPLICATION_STATUS_CHANGED -> new NotificationTemplate(
                    "Application status updated",
                    "Application " + reference + " changed from "
                            + plain(payload.previousStatus()) + " to "
                            + plain(payload.currentStatus()) + ".");
            case APPLICATION_WITHDRAWN -> new NotificationTemplate(
                    "Application withdrawn",
                    "Application " + reference + " was withdrawn.");
        };
    }

    private static String plain(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n<>]", " ").trim();
    }
}

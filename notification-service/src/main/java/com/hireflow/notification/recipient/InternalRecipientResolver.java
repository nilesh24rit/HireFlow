package com.hireflow.notification.recipient;

import java.util.List;

import org.springframework.stereotype.Component;

import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.notification.domain.NotificationType;

/**
 * Resolves identities from event-owned identifiers. Contact lookup is delegated
 * to the owning service through the injected directory, never to a local user table.
 */
@Component
public class InternalRecipientResolver implements RecipientResolver {

    private final RecipientDirectory directory;

    public InternalRecipientResolver(RecipientDirectory directory) {
        this.directory = directory;
    }

    @Override
    public List<Recipient> resolve(ApplicationEvent event, NotificationType type) {
        var payload = event.payload();
        if (type == NotificationType.APPLICATION_SUBMITTED_RECRUITER
                && payload.recruiterUserId() != null) {
            return directory.findByUserId(payload.recruiterUserId()).stream().toList();
        }
        return directory.findByUserId(payload.candidateId()).stream().toList();
    }
}

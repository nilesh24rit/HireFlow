package com.hireflow.notification.recipient;

import java.util.List;

import com.hireflow.contracts.events.ApplicationEvent;
import com.hireflow.notification.domain.NotificationType;

public interface RecipientResolver {

    List<Recipient> resolve(ApplicationEvent event, NotificationType type);
}

package com.hireflow.notification.delivery;

import com.hireflow.notification.domain.Notification;
import com.hireflow.notification.recipient.Recipient;

public interface NotificationMailSender {

    void send(Notification notification, Recipient recipient);
}

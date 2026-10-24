package com.hireflow.notification.delivery;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.hireflow.notification.config.MailInfrastructureProperties;
import com.hireflow.notification.domain.Notification;
import com.hireflow.notification.recipient.Recipient;

@Component
public class SmtpNotificationMailSender implements NotificationMailSender {

    private final JavaMailSender mailSender;
    private final MailInfrastructureProperties properties;

    public SmtpNotificationMailSender(JavaMailSender mailSender,
            MailInfrastructureProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(Notification notification, Recipient recipient) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getSender());
        message.setTo(recipient.email());
        message.setSubject(notification.subject());
        message.setText(notification.message());
        mailSender.send(message);
    }
}

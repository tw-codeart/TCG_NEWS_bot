package com.twcodeart.tcgnews.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsoleNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotificationSender.class);

    @Override
    public void send(String message) {
        log.info("Notification:\n{}", message);
    }
}

package com.twcodeart.tcgnews.notification;

/** Sends an already formatted message without accessing news storage. */
public interface NotificationSender {

    void send(String message);
}

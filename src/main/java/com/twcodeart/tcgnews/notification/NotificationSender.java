package com.twcodeart.tcgnews.notification;

/** Sends an already formatted message without accessing news storage. */
public interface NotificationSender {

    void send(String message);

    default boolean acceptsImages() { return false; }

    /** Image URLs are transport metadata, never appended to the message as links. */
    default void send(String message, String imageUrl) { send(message); }

    default void sendBatch(java.util.List<NotificationMessage> messages) {
        for (var message : messages) { send(message.text(), message.imageUrl()); }
    }
}

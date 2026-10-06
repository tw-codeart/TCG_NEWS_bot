package com.twcodeart.tcgnews.notification.qq;

/** Contains safe failure details, never the access token or upstream response body. */
public class QqNotificationException extends RuntimeException {

    public enum Reason { CONFIGURATION, UNAVAILABLE, TIMEOUT, API_FAILURE, FORMATTING_FAILURE }

    private final Reason reason;

    public QqNotificationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

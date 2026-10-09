package com.twcodeart.tcgnews.notification;

/** Already formatted text with separately supplied, optional image transport metadata. */
public record NotificationMessage(String text, String imageUrl) { }

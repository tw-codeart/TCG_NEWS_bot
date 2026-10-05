package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.NewsItem;

/**
 * Formats source metadata as text without saving or sending it.
 * Future notification transports can consume the returned message.
 */
public interface NotificationFormatter {

    String format(NewsItem newsItem);
}

package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(NewsNotificationService.class);
    private final NotificationFormatter formatter;
    private final NotificationSender sender;

    public NewsNotificationService(NotificationFormatter formatter, NotificationSender sender) {
        this.formatter = formatter;
        this.sender = sender;
    }

    /** Called with the current fetch's successfully saved records, after persistence. */
    public void notifyNewItems(List<NewsItem> savedItems) {
        for (NewsItem item : savedItems) {
            try {
                sender.send(formatter.format(item));
            } catch (RuntimeException exception) {
                log.error("Notification failed for saved news: id={}, sourceUrl={}",
                        item.getId(), item.getSourceUrl(), exception);
            }
        }
    }
}

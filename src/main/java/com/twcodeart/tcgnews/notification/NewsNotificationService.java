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
        notifyNewItems(savedItems, sender);
    }

    public DeliveryResult notifyNewItems(List<NewsItem> savedItems, NotificationSender destination) {
        int sent = 0;
        int failed = 0;
        for (NewsItem item : savedItems) {
            try {
                destination.send(formatter.format(item));
                sent++;
            } catch (RuntimeException exception) {
                failed++;
                log.error("Notification failed for saved news: id={}, sourceUrl={}",
                        item.getId(), item.getSourceUrl(), exception);
            }
        }
        return new DeliveryResult(sent, failed);
    }

    public record DeliveryResult(int sent, int failed) { }
}

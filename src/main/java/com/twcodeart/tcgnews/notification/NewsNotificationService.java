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
                String message = formatter.format(item);
                if (destination.acceptsImages()) {
                    destination.send(message, item.getImageUrl());
                } else {
                    destination.send(message);
                }
                sent++;
            } catch (RuntimeException exception) {
                failed++;
                log.error("Notification failed for saved news: id={}, sourceUrl={}",
                        item.getId(), item.getSourceUrl(), exception);
            }
        }
        return new DeliveryResult(sent, failed);
    }

    /** One packet can contain several newly saved records without changing their identity. */
    public DeliveryResult notifyBatch(List<NewsItem> savedItems, NotificationSender destination) {
        var messages = new java.util.ArrayList<NotificationMessage>();
        int failed = 0;
        for (var item : savedItems) {
            try { messages.add(new NotificationMessage(formatter.format(item), item.getImageUrl())); }
            catch (RuntimeException exception) {
                failed++;
                log.error("Batch formatting failed for saved news: id={}", item.getId(), exception);
            }
        }
        if (messages.isEmpty()) { return new DeliveryResult(0, failed); }
        try {
            destination.sendBatch(List.copyOf(messages));
            return new DeliveryResult(messages.size(), failed);
        } catch (RuntimeException exception) {
            log.error("Batch notification failed: newsItems={}", messages.size(), exception);
            return new DeliveryResult(0, failed + messages.size());
        }
    }

    public record DeliveryResult(int sent, int failed) { }

    /** Formats every record before sending; the durable caller acknowledges only a complete packet. */
    public void sendCompleteBatch(List<NewsItem> items, NotificationSender destination) {
        var messages = items.stream().map(item -> new NotificationMessage(formatter.format(item), item.getImageUrl())).toList();
        if (destination.acceptsImages()) destination.sendBatch(messages);
        else {
            if (messages.size() != 1) throw new IllegalArgumentException("A text delivery must contain one item");
            destination.send(messages.get(0).text());
        }
    }
}

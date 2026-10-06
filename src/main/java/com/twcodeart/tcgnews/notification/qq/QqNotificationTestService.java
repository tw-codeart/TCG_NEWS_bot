package com.twcodeart.tcgnews.notification.qq;

import com.twcodeart.tcgnews.news.NewsQueryService;
import com.twcodeart.tcgnews.notification.NotificationFormatter;
import com.twcodeart.tcgnews.notification.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QqNotificationTestService {

    private static final Logger log = LoggerFactory.getLogger(QqNotificationTestService.class);
    private final QqProperties properties;
    private final ObjectProvider<QqNotificationSender> senders;
    private final NotificationFormatter formatter;
    private final NewsQueryService query;

    public QqNotificationTestService(QqProperties properties, ObjectProvider<QqNotificationSender> senders,
                                     NotificationFormatter formatter, NewsQueryService query) {
        this.properties = properties;
        this.senders = senders;
        this.formatter = formatter;
        this.query = query;
    }

    public Result sendTest() {
        NotificationSender sender = configuredSender();
        try {
            sender.send("[TCG News Bot QQ Test]\n今日のカード notification smoke test.\nhttps://ws-tcg.com/");
            return new Result(1, 1, List.of());
        } catch (QqNotificationException exception) {
            return failure(1, 0, exception, null);
        }
    }

    public Result sendStoredNews(int limit) {
        if (limit < 1 || limit > 5) {
            throw new IllegalArgumentException("limit must be between 1 and 5");
        }
        NotificationSender sender = configuredSender();
        int sent = 0;
        for (var item : query.findLatestNews(limit).stream().limit(limit).toList()) {
            try {
                String message;
                try {
                    message = formatter.format(item);
                } catch (RuntimeException exception) {
                    throw new QqNotificationException(QqNotificationException.Reason.FORMATTING_FAILURE,
                            "Notification formatting failed");
                }
                sender.send(message);
                sent++;
            } catch (QqNotificationException exception) {
                return failure(sent + 1, sent, exception, item.getId());
            }
        }
        return new Result(sent, sent, List.of());
    }

    private NotificationSender configuredSender() {
        properties.requireConfigured();
        var sender = senders.getIfAvailable();
        if (sender == null) {
            throw new QqNotificationException(QqNotificationException.Reason.CONFIGURATION, "QQ sender is not configured");
        }
        return sender;
    }

    private Result failure(int attempted, int sent, QqNotificationException exception, Long newsId) {
        log.warn("QQ smoke notification failed: newsId={}, reason={}", newsId, exception.getReason());
        return new Result(attempted, sent, List.of(new Error(exception.getReason(), exception.getMessage())));
    }

    public record Error(QqNotificationException.Reason reason, String message) { }

    public record Result(int attempted, int sent, List<Error> errors) {
        public Result { errors = List.copyOf(errors); }
    }
}

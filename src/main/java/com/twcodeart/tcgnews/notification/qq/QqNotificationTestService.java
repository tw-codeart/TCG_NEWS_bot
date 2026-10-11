package com.twcodeart.tcgnews.notification.qq;

import com.twcodeart.tcgnews.diagnostics.DiagnosticIds;

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
            sender.send("[TCG News Bot QQ Test]\n今日のカード notification smoke test.");
            return new Result(1, 1, List.of());
        } catch (QqNotificationException exception) {
            return failure(1, 0, exception, null);
        }
    }

    public Result sendStoredNews(int limit) {
        return sendStoredNews(limit, null);
    }

    public Result sendStoredNews(int limit, com.twcodeart.tcgnews.news.Game game) {
        if (limit < 1 || limit > 5) {
            throw new IllegalArgumentException("limit must be between 1 and 5");
        }
        NotificationSender sender = configuredSender();
        int sent = 0;
        var stored = game == null ? query.findLatestNews(limit) : query.findLatestNews(limit, game);
        for (var item : stored.stream().limit(limit).toList()) {
            try {
                String message;
                try {
                    message = formatter.format(item);
                } catch (RuntimeException exception) {
                    throw new QqNotificationException(QqNotificationException.Reason.FORMATTING_FAILURE,
                            "Notification formatting failed");
                }
                if (sender.acceptsImages()) {
                    sender.send(message, item.getImageUrl());
                } else {
                    sender.send(message);
                }
                sent++;
            } catch (QqNotificationException exception) {
                return failure(sent + 1, sent, exception, item.getId());
            }
        }
        return new Result(sent, sent, List.of());
    }

    public PostResult sendStoredPosts(int limit, com.twcodeart.tcgnews.news.Game game) {
        return sendStoredPosts(limit,game,null);
    }

    public PostResult sendStoredPosts(int limit, com.twcodeart.tcgnews.news.Game game,String postId) {
        if (limit<1 || limit>5 || game==null) throw new IllegalArgumentException("Use a supported game and 1 to 5 posts");
        if(postId!=null && (limit!=1 || !postId.matches("[1-9][0-9]{0,19}"))) {
            throw new IllegalArgumentException("postId must be a numeric X post ID and limit must be 1");
        }
        NotificationSender sender = configuredSender();
        var posts=postId==null ? query.findLatestXPosts(game,limit) : query.findXPost(game,postId);
        var packets=com.twcodeart.tcgnews.notification.NewsForwardPackets.plan(
                posts.stream().flatMap(List::stream).toList(),QqNotificationSender.MAX_FORWARD_ITEMS);
        if(packets.size()>5) throw new IllegalArgumentException("Selected posts exceed five forward packets; lower the post limit");
        int images=(int)posts.stream().flatMap(List::stream)
                .filter(item -> item.getImageUrl()!=null && !item.getImageUrl().isBlank()).count();
        int sent=0;
        for(var packet:packets) {
            try {
                var messages=new java.util.ArrayList<com.twcodeart.tcgnews.notification.NotificationMessage>();
                for(var item:packet) {
                    try { messages.add(new com.twcodeart.tcgnews.notification.NotificationMessage(formatter.format(item),item.getImageUrl())); }
                    catch(RuntimeException exception) {
                        throw new QqNotificationException(QqNotificationException.Reason.FORMATTING_FAILURE,"Notification formatting failed");
                    }
                }
                sender.sendBatch(List.copyOf(messages)); sent++;
            } catch(QqNotificationException exception) {
                String id = DiagnosticIds.current();
                log.warn("QQ post preview failed: errorId={}, game={}, reason={}",id,game,exception.getReason(),exception);
                return new PostResult(posts.size(),images,sent+1,sent,List.of(new Error(exception.getReason(),exception.getMessage(),id)));
            }
        }
        return new PostResult(posts.size(),images,sent,sent,List.of());
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
        String id = DiagnosticIds.current();
        log.warn("QQ smoke notification failed: errorId={}, newsId={}, reason={}", id, newsId, exception.getReason(),exception);
        return new Result(attempted, sent, List.of(new Error(exception.getReason(), exception.getMessage(),id)));
    }

    public record Error(QqNotificationException.Reason reason, String message, String errorId) { }

    public record PostResult(int posts,int images,int attempted,int sent,List<Error> errors) {
        public PostResult { errors=List.copyOf(errors); }
    }

    public record Result(int attempted, int sent, List<Error> errors) {
        public Result { errors = List.copyOf(errors); }
    }
}

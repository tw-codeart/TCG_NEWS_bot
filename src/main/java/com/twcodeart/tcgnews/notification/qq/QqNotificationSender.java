package com.twcodeart.tcgnews.notification.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.twcodeart.tcgnews.notification.NotificationSender;
import com.twcodeart.tcgnews.notification.ocr.CardTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.*;

@Component
@ConditionalOnProperty(prefix = "qq", name = "enabled", havingValue = "true")
public class QqNotificationSender implements NotificationSender {
    public static final int MAX_FORWARD_ITEMS = 20;
    private static final Logger log = LoggerFactory.getLogger(QqNotificationSender.class);
    private final QqProperties properties;
    private final QqApiClient api;
    private final CardTextExtractor extractor;
    private final QqForwardMessageFormatter formatter = new QqForwardMessageFormatter();

    @Autowired
    public QqNotificationSender(QqProperties properties) {
        this(properties, httpClient());
    }

    QqNotificationSender(QqProperties properties, RestClient client) {
        this.properties = properties;
        this.api = new QqApiClient(properties, client);
        this.extractor = new NapCatCardTextExtractor(api);
    }

    private static RestClient httpClient() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean acceptsImages() {
        return properties.isImageForwardEnabled();
    }

    @Override
    public void send(String message) {
        properties.requireConfigured();
        String text = acceptsImages() ? formatter.withoutLinks(message) : message;
        confirmSent(api.call("send_group_msg", Map.of("group_id", properties.groupId(), "message",
                List.of(segment("text", "text", text)))));
    }

    @Override
    public void send(String message, String imageUrl) {
        properties.requireConfigured();
        if (!acceptsImages() || imageUrl == null || imageUrl.isBlank()) {
            send(message);
            return;
        }
        requireOfficialImage(imageUrl);
        String extracted = null;
        if (properties.isOcrEnabled()) {
            try {
                extracted = extractor.extract(imageUrl);
            } catch (QqNotificationException exception) {
                log.warn("Card OCR failed; original image will still be sent: reason={}", exception.getReason());
            }
        }
        // Use the logged-in bot's identity, not a fabricated official author.
        var login = api.call("get_login_info", Map.of());
        String userId = login.path("user_id").asText();
        if (!userId.matches("[1-9][0-9]*")) {
            throw new QqNotificationException(API_FAILURE, "QQ API returned an invalid bot identity");
        }
        String nickname = login.path("nickname").asText("TCG News Bot");
        var metadata = node(userId, nickname, List.of(segment("text", "text", formatter.withoutLinks(message))));
        var card = node(userId, nickname, List.of(segment("image", "file", imageUrl),
                segment("text", "text", formatter.ocrText(extracted, properties.isOcrEnabled()))));
        confirmSent(api.call("send_group_forward_msg", Map.of("group_id", properties.groupId(),
                "messages", List.of(metadata, card))));
    }

    @Override
    public void sendBatch(List<com.twcodeart.tcgnews.notification.NotificationMessage> messages) {
        properties.requireConfigured();
        if (messages.isEmpty()) { return; }
        if (messages.size() > MAX_FORWARD_ITEMS) {
            throw new IllegalArgumentException("A forward packet accepts at most 20 news items");
        }
        // Validate the whole packet before making any OCR or send requests.
        for (var message : messages) {
            if (message.imageUrl() != null && !message.imageUrl().isBlank()) { requireOfficialImage(message.imageUrl()); }
        }
        var login = api.call("get_login_info", Map.of());
        String userId = login.path("user_id").asText();
        if (!userId.matches("[1-9][0-9]*")) {
            throw new QqNotificationException(API_FAILURE, "QQ API returned an invalid bot identity");
        }
        String nickname = login.path("nickname").asText("TCG News Bot");
        var nodes = new java.util.ArrayList<Map<String, Object>>();
        for (var message : messages) {
            nodes.add(node(userId, nickname, List.of(segment("text", "text", formatter.withoutLinks(message.text())))));
            if (message.imageUrl() == null || message.imageUrl().isBlank()) { continue; }
            String extracted = null;
            if (properties.isOcrEnabled()) {
                try { extracted = extractor.extract(message.imageUrl()); }
                catch (QqNotificationException exception) {
                    log.warn("Batch card OCR failed; original image retained: reason={}", exception.getReason());
                }
            }
            nodes.add(node(userId, nickname, List.of(segment("image", "file", message.imageUrl()),
                    segment("text", "text", formatter.ocrText(extracted, properties.isOcrEnabled())))));
        }
        confirmSent(api.call("send_group_forward_msg", Map.of("group_id", properties.groupId(), "messages", nodes)));
    }

    private Map<String, Object> segment(String type, String key, String value) {
        return Map.of("type", type, "data", Map.of(key, value));
    }

    private Map<String, Object> node(String userId, String nickname, List<Map<String, Object>> content) {
        return Map.of("type", "node", "data", Map.of("user_id", userId, "nickname", nickname, "content", content));
    }

    private void confirmSent(JsonNode response) {
        if (!response.path("message_id").isIntegralNumber()) {
            throw new QqNotificationException(API_FAILURE, "QQ API did not confirm successful message sending");
        }
    }

    private void requireOfficialImage(String imageUrl) {
        try {
            var uri = URI.create(imageUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || host == null
                    || !(host.equalsIgnoreCase("pbs.twimg.com") || host.equalsIgnoreCase("ws-tcg.com")
                    || host.toLowerCase(java.util.Locale.ROOT).endsWith(".ws-tcg.com"))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new QqNotificationException(CONFIGURATION, "Card image must use an official WS or X image HTTPS URL");
        }
    }
}

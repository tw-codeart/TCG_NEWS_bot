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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.*;

@Component
@ConditionalOnProperty(prefix = "qq", name = "enabled", havingValue = "true")
public class QqNotificationSender implements NotificationSender {
    // Internal bounded batch size; this is not a claim about QQ's official maximum.
    public static final int MAX_FORWARD_ITEMS = 50;
    private static final Logger log = LoggerFactory.getLogger(QqNotificationSender.class);
    private final QqProperties properties;
    private final QqApiClient api;
    private final CardTextExtractor extractor;
    private final RestClient client;
    private final OcrDiagnostics ocrDiagnostics;
    private final QqForwardMessageFormatter formatter = new QqForwardMessageFormatter();

    @Autowired
    public QqNotificationSender(QqProperties properties, OcrDiagnostics diagnostics) {
        this(properties, httpClient(), diagnostics);
    }

    QqNotificationSender(QqProperties properties, RestClient client) {
        this(properties, client, new OcrDiagnostics());
    }

    private QqNotificationSender(QqProperties properties, RestClient client, OcrDiagnostics diagnostics) {
        this.properties = properties;
        this.client = client;
        this.api = new QqApiClient(properties, client);
        this.ocrDiagnostics = diagnostics;
        this.extractor = "tesseract".equals(properties.getOcrProvider())
                ? new TesseractCardTextExtractor(properties) : new OneBotCardTextExtractor(api);
    }

    private static RestClient httpClient() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory).build();
    }

    /** Only trusted command/subscription code supplies this destination; REST preview stays fixed-group. */
    public NotificationSender forSubscribedGroup(long groupId){
        if(groupId<1)throw new IllegalArgumentException("Invalid group");
        var bound=new QqProperties();bound.setEnabled(properties.isEnabled());bound.setApiBaseUrl(properties.getApiBaseUrl());
        bound.setAdapter(properties.getAdapter());
        bound.setAccessToken(properties.getAccessToken());bound.setTestGroupId(Long.toString(groupId));
        bound.setImageForwardEnabled(properties.isImageForwardEnabled());bound.setOcrEnabled(properties.isOcrEnabled());
        bound.setOcrProvider(properties.getOcrProvider());
        bound.setTesseractExecutable(properties.getTesseractExecutable());
        bound.setTesseractDataDirectory(properties.getTesseractDataDirectory());
        return new QqNotificationSender(bound,client,ocrDiagnostics);
    }
    public long botUserId(){
        String value=api.call("get_login_info",Map.of()).path("user_id").asText();
        try{long id=Long.parseLong(value);if(id>0)return id;}catch(NumberFormatException ignored){ }
        throw new QqNotificationException(API_FAILURE,"QQ API returned an invalid bot identity");
    }
    public String memberRole(long groupId,long userId){
        var member=api.call("get_group_member_info",Map.of("group_id",groupId,"user_id",userId,"no_cache",true));
        if(member.path("group_id").asLong(-1)!=groupId||member.path("user_id").asLong(-1)!=userId)
            throw new QqNotificationException(API_FAILURE,"QQ API member identity did not match");
        String role=member.path("role").asText();
        if(!java.util.Set.of("owner","admin","member").contains(role))throw new QqNotificationException(API_FAILURE,"QQ API returned an invalid member role");
        return role;
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
        String extracted = extractSafely(imageUrl);
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
            throw new IllegalArgumentException("A forward packet accepts at most 50 news items");
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
        var displayedTexts = new java.util.HashSet<String>();
        for (var message : messages) {
            String text = formatter.withoutLinks(message.text());
            // Presentation-only deduplication: never drop an image or change stored news/delivery identity.
            if (displayedTexts.add(text)) nodes.add(node(userId, nickname, List.of(segment("text", "text", text))));
            if (message.imageUrl() == null || message.imageUrl().isBlank()) { continue; }
            String extracted = extractSafely(message.imageUrl());
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
        OfficialCardImage.require(imageUrl);
    }

    private String extractSafely(String imageUrl) {
        if (!properties.isOcrEnabled()) return null;
        try {
            String text = extractor.extract(imageUrl);
            String result = text == null || text.isBlank() ? "EMPTY" : "SUCCESS";
            ocrDiagnostics.record(properties.getOcrProvider(), result);
            if ("EMPTY".equals(result)) log.warn("Card OCR returned no text: provider={}, errorId={}",
                    properties.getOcrProvider(), com.twcodeart.tcgnews.diagnostics.DiagnosticIds.current());
            return text;
        } catch (QqNotificationException exception) {
            ocrDiagnostics.record(properties.getOcrProvider(), exception.getReason().name());
            log.warn("Card OCR failed; original image retained: provider={}, reason={}, detail={}, errorId={}",
                    properties.getOcrProvider(), exception.getReason(), exception.getMessage(),
                    com.twcodeart.tcgnews.diagnostics.DiagnosticIds.current());
            return null;
        }
    }
}

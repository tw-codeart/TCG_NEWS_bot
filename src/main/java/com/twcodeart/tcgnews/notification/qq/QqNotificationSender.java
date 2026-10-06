package com.twcodeart.tcgnews.notification.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.twcodeart.tcgnews.notification.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.*;

@Component
@ConditionalOnProperty(prefix = "qq", name = "enabled", havingValue = "true")
public class QqNotificationSender implements NotificationSender {

    private final QqProperties properties;
    private final RestClient client;

    @Autowired
    public QqNotificationSender(QqProperties properties) {
        this(properties, httpClient());
    }

    QqNotificationSender(QqProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    private static RestClient httpClient() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void send(String message) {
        properties.requireConfigured();
        var payload = Map.of("group_id", properties.groupId(), "message",
                List.of(Map.of("type", "text", "data", Map.of("text", message))));
        try {
            var request = client.post().uri(properties.actionUri()).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (properties.getAccessToken() != null && !properties.getAccessToken().isBlank()) {
                request.header("Authorization", "Bearer " + properties.getAccessToken());
            }
            JsonNode response = request.body(payload).retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (sentRequest, received) -> {
                        throw new QqNotificationException(API_FAILURE,
                                "QQ API returned HTTP " + received.getStatusCode().value());
                    }).body(JsonNode.class);
            if (response == null || !"ok".equals(response.path("status").asText())
                    || !response.path("retcode").isIntegralNumber() || !response.path("retcode").canConvertToInt()
                    || response.path("retcode").intValue() != 0
                    || !response.path("data").path("message_id").isIntegralNumber()) {
                throw new QqNotificationException(API_FAILURE, "QQ API did not confirm successful message sending");
            }
        } catch (RestClientResponseException exception) {
            throw new QqNotificationException(API_FAILURE, "QQ API returned HTTP " + exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                    throw new QqNotificationException(TIMEOUT, "QQ API request timed out");
                }
            }
            throw new QqNotificationException(UNAVAILABLE, "QQ API is unavailable");
        } catch (RestClientException exception) {
            throw new QqNotificationException(API_FAILURE, "QQ API returned an invalid response");
        }
    }
}

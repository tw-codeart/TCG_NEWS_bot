package com.twcodeart.tcgnews.notification.qq;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;

import static com.twcodeart.tcgnews.notification.qq.QqNotificationException.Reason.*;

final class QqApiClient {
    private final QqProperties properties;
    private final RestClient client;

    QqApiClient(QqProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    JsonNode call(String action, Map<String, ?> payload) {
        properties.requireConfigured();
        try {
            var request = client.post().uri(properties.actionUri(action)).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (properties.getAccessToken() != null && !properties.getAccessToken().isBlank()) {
                request.header("Authorization", "Bearer " + properties.getAccessToken());
            }
            JsonNode response = request.body(payload).retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (sent, received) -> {
                        throw new QqNotificationException(API_FAILURE,
                                "QQ API returned HTTP " + received.getStatusCode().value());
                    }).body(JsonNode.class);
            if (response == null || !"ok".equals(response.path("status").asText())
                    || !response.path("retcode").isIntegralNumber() || !response.path("retcode").canConvertToInt()
                    || response.path("retcode").intValue() != 0 || response.path("data").isMissingNode()) {
                throw new QqNotificationException(API_FAILURE, "QQ API did not confirm successful operation");
            }
            return response.path("data");
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

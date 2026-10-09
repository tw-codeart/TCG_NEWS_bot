package com.twcodeart.tcgnews.notification.qq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QqNotificationSenderTest {

    private QqProperties properties;
    private MockRestServiceServer server;
    private QqNotificationSender sender;
    private static final String ENDPOINT = "http://qq-api.example/send_group_msg";
    private static final String SUCCESS = "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}";

    @BeforeEach
    void configureSyntheticEndpoint() {
        properties = new QqProperties();
        properties.setEnabled(true);
        properties.setImageForwardEnabled(false);
        properties.setApiBaseUrl("http://qq-api.example/");
        properties.setTestGroupId("42"); // Synthetic test ID, not a real group configuration.
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new QqNotificationSender(properties, builder.build());
    }

    @Test
    void disabledSenderAndMissingConfigurationMakeNoRequest() {
        properties.setEnabled(false);
        assertThatThrownBy(() -> sender.send("Test")).isInstanceOf(QqNotificationException.class)
                .hasMessage("QQ notifications are disabled");
        properties.setEnabled(true);
        properties.setTestGroupId("");
        assertThatThrownBy(() -> sender.send("Test")).isInstanceOf(QqNotificationException.class)
                .hasMessageContaining("qq.test-group-id");
        properties.setTestGroupId("42");
        properties.setApiBaseUrl("");
        assertThatThrownBy(() -> sender.send("Test")).isInstanceOf(QqNotificationException.class)
                .hasMessageContaining("qq.api-base-url");
        server.verify();
    }

    @Test
    void usesOnlyConfiguredGroupAndPreservesJapaneseUrlsAndLiteralText() {
        String text = "今日のカード テスト\nhttps://ws-tcg.com/products/test/?a=1&b=2\n[CQ:at,qq=all]";
        properties.setAccessToken("synthetic-test-token");
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer synthetic-test-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(request -> {
                    var body = new ObjectMapper().readTree(((MockClientHttpRequest) request).getBodyAsBytes());
                    assertThat(body.path("group_id").longValue()).isEqualTo(42);
                    assertThat(body.path("message").size()).isEqualTo(1);
                    assertThat(body.path("message").get(0).path("type").asText()).isEqualTo("text");
                    assertThat(body.path("message").get(0).path("data").path("text").asText()).isEqualTo(text);
                }).andRespond(withSuccess(SUCCESS, MediaType.APPLICATION_JSON));
        sender.send(text);
        server.verify();
    }

    @Test
    void rejectsNon2xxIncludingRedirectsWithoutExposingResponseBodies() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .body("upstream-private-details"));
        assertThatThrownBy(() -> sender.send("Test")).isInstanceOf(QqNotificationException.class)
                .hasMessage("QQ API returned HTTP 503");
        server.verify();
        server.reset();
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.FOUND)
                .body(SUCCESS).contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> sender.send("Test")).hasMessage("QQ API returned HTTP 302");
        server.verify();
    }

    @Test
    void distinguishesConnectionFailureAndTimeout() {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new IOException("Unavailable")));
        var unavailable = catchThrowableOfType(() -> sender.send("Test"), QqNotificationException.class);
        assertThat(unavailable.getReason()).isEqualTo(QqNotificationException.Reason.UNAVAILABLE);
        server.verify();
        server.reset();
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new HttpTimeoutException("Timed out")));
        var timeout = catchThrowableOfType(() -> sender.send("Test"), QqNotificationException.class);
        assertThat(timeout.getReason()).isEqualTo(QqNotificationException.Reason.TIMEOUT);
        server.verify();
    }

    @Test
    void rejectsLogicalFailureAsyncAndIncompleteOrMalformedResponses() {
        for (String response : new String[] {
                "{\"status\":\"failed\",\"retcode\":100,\"message\":\"private-details\"}",
                "{\"status\":\"async\",\"retcode\":1}",
                "{\"status\":\"ok\"}",
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{}}", "not JSON"}) {
            server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            var failure = catchThrowableOfType(() -> sender.send("Test"), QqNotificationException.class);
            assertThat(failure.getReason()).isEqualTo(QqNotificationException.Reason.API_FAILURE);
            assertThat(failure.getMessage()).doesNotContain("private-details");
            server.verify();
            server.reset();
        }
    }
}

package com.twcodeart.tcgnews.notification.qq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twcodeart.tcgnews.notification.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Synthetic responses matching the inspected LLBot OneBot contract; no live QQ account. */
class OneBotAdapterCompatibilityTest {
    private static final String BASE = "http://onebot.example/";

    @ParameterizedTest
    @ValueSource(strings = {"napcat", "llbot"})
    void forwardsEveryImageWithSharedOcrAndOneCopyOfJapanesePostText(String adapter) {
        var properties = properties(adapter);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = new QqNotificationSender(properties, builder.build()).forSubscribedGroup(84);
        var images = List.of("https://pbs.twimg.com/media/first.png", "https://pbs.twimg.com/media/second.png",
                "https://pbs.twimg.com/media/third.png");
        server.expect(requestTo(BASE + "get_login_info"))
                .andExpect(header("Authorization", "Bearer synthetic-token"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"user_id\":123,\"nickname\":\"Test bot\"}}", MediaType.APPLICATION_JSON));
        for (String image : images) {
            server.expect(requestTo(BASE + "ocr_image"))
                    .andExpect(content().json("{\"image\":\"" + image + "\"}"))
                    .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"texts\":[{\"text\":\"効果テスト\",\"confidence\":95,\"coordinates\":[]}],\"language\":\"ja\"}}", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo(BASE + "send_group_forward_msg"))
                .andExpect(request -> {
                    var body = new ObjectMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.path("group_id").asLong()).isEqualTo(84);
                    var nodes = body.path("messages");
                    assertThat(nodes.size()).isEqualTo(4); // One shared text node and three distinct images.
                    assertThat(nodes.get(0).path("data").path("content").get(0).path("data").path("text").asText())
                            .contains("今日のカード").doesNotContain("https://");
                    for (int i = 0; i < images.size(); i++) {
                        var data = nodes.get(i + 1).path("data");
                        assertThat(data.path("user_id").asText()).isEqualTo("123");
                        assertThat(data.path("nickname").asText()).isEqualTo("Test bot");
                        assertThat(data.path("content").get(0).path("data").path("file").asText()).isEqualTo(images.get(i));
                        assertThat(data.path("content").get(1).path("data").path("text").asText()).contains("効果テスト");
                    }
                })
                .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":987,\"forward_id\":\"synthetic-forward\"}}", MediaType.APPLICATION_JSON));
        sender.sendBatch(images.stream().map(image -> new NotificationMessage(
                "今日のカード テスト\nhttps://x.com/wstcg/status/123", image)).toList());
        server.verify();
    }

    @Test
    void rejectsUnconfirmedLlbotForwardRatherThanTreatingItAsSent() {
        var properties = properties("llbot");
        properties.setOcrEnabled(false);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = new QqNotificationSender(properties, builder.build());
        server.expect(requestTo(BASE + "get_login_info"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"user_id\":123}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "send_group_forward_msg"))
                .andRespond(withSuccess("{\"status\":\"failed\",\"retcode\":1200,\"data\":null}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> sender.sendBatch(List.of(new NotificationMessage("新カード", "https://pbs.twimg.com/media/card.png"))))
                .isInstanceOf(QqNotificationException.class);
        server.verify();
    }

    @Test
    void keepsLegacyDefaultsAndRejectsUnsupportedAdapters() {
        var properties = new QqProperties();
        assertThat(properties.getAdapter()).isEqualTo("napcat");
        assertThat(properties.getOcrProvider()).isEqualTo("napcat");
        assertThatThrownBy(() -> properties.setAdapter("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setOcrProvider("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    private QqProperties properties(String adapter) {
        var properties = new QqProperties();
        properties.setEnabled(true);
        properties.setAdapter(adapter);
        properties.setApiBaseUrl(BASE);
        properties.setTestGroupId("42");
        properties.setAccessToken("synthetic-token");
        properties.setOcrProvider("onebot");
        return properties;
    }
}

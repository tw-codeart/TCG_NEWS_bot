package com.twcodeart.tcgnews.notification.qq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QqImageNotificationSenderTest {
    private static final String BASE = "http://qq-api.example/";
    private static final String IMAGE = "https://pbs.twimg.com/media/sample-card.png";
    private static final String SENT = "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}";
    private QqProperties properties;
    private MockRestServiceServer server;
    private QqNotificationSender sender;

    @BeforeEach
    void configure() {
        properties = new QqProperties();
        properties.setEnabled(true);
        properties.setTestGroupId("42");
        properties.setApiBaseUrl(BASE);
        properties.setAccessToken("synthetic-token");
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new QqNotificationSender(properties, builder.build());
    }

    @Test
    void sendsOriginalImageAndUnverifiedJapaneseOcrInForwardNodesWithoutTextLinks() {
        assertThat(sender.acceptsImages()).isTrue();
        server.expect(requestTo(BASE + "ocr_image")).andExpect(header("Authorization", "Bearer synthetic-token"))
                .andExpect(content().json("{\"image\":\"" + IMAGE + "\"}"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"texts\":["
                        + "{\"text\":\"【自】このカードが舞台に置かれた時\"},{\"text\":null},"
                        + "{\"text\":\"あなたのキャラを1枚選ぶ。\"}]}}", MediaType.APPLICATION_JSON));
        expectLogin();
        expectForward("【自】このカードが舞台に置かれた時", "あなたのキャラを1枚選ぶ。", "未校对");
        sender.send("今日のカード テスト\nTitle: 公開 https://t.co/example\nOfficial source:\n"
                + "https://x.com/wstcg/status/123\nImage:\n" + IMAGE, IMAGE);
        server.verify();
    }

    @Test
    void ocrTimeoutStillSendsOriginalImageWithExplicitFallback() {
        server.expect(requestTo(BASE + "ocr_image")).andRespond(withException(new HttpTimeoutException("Timed out")));
        expectLogin();
        expectForward("OCR 未识别到文字或识别失败", "原卡图");
        sender.send("今日のカード https://x.com/wstcg/status/123", IMAGE);
        server.verify();
    }

    @Test
    void malformedOcrResponseStillSendsImageWithoutInventingText() {
        server.expect(requestTo(BASE + "ocr_image")).andRespond(withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"texts\":null}}", MediaType.APPLICATION_JSON));
        expectLogin();
        expectForward("OCR 未识别到文字或识别失败");
        sender.send("今日のカード", IMAGE);
        server.verify();
    }

    @Test
    void disabledOcrMakesNoOcrRequestButStillForwardsTheOriginalImage() {
        properties.setOcrEnabled(false);
        expectLogin();
        expectForward("OCR 未启用");
        sender.send("今日のカード", IMAGE);
        server.verify();
    }

    @Test
    void absentImageUsesLinkFreeTextWithoutOcrOrForwardRequest() {
        server.expect(requestTo(BASE + "send_group_msg")).andExpect(request -> {
            var body = body(request);
            assertThat(body.path("group_id").asLong()).isEqualTo(42);
            assertThat(body.path("message").get(0).path("data").path("text").asText())
                    .contains("お知らせ").doesNotContain("https://", "Official source:");
        }).andRespond(withSuccess(SENT, MediaType.APPLICATION_JSON));
        sender.send("お知らせ\nOfficial source:\nhttps://ws-tcg.com/information/test/", null);
        server.verify();
    }

    @Test
    void disabledSenderAndUntrustedImageMakeNoHttpRequests() {
        properties.setEnabled(false);
        assertThatThrownBy(() -> sender.send("Test", IMAGE)).hasMessage("QQ notifications are disabled");
        properties.setEnabled(true);
        for (String image : new String[]{"http://127.0.0.1/a.png", "https://attacker.example/a.png", "file:///test.png"}) {
            assertThatThrownBy(() -> sender.send("Test", image)).isInstanceOf(QqNotificationException.class);
        }
        server.verify();
    }

    @Test
    void forwardApiFailureIsReportedInsteadOfClaimingDelivery() {
        properties.setOcrEnabled(false);
        expectLogin();
        server.expect(requestTo(BASE + "send_group_forward_msg")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> sender.send("Test", IMAGE)).hasMessage("QQ API returned HTTP 502");
        server.verify();
    }

    @Test
    void invalidBotIdentityPreventsAForwardWithInventedAuthor() {
        properties.setOcrEnabled(false);
        server.expect(requestTo(BASE + "get_login_info")).andRespond(withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> sender.send("Test", IMAGE)).hasMessage("QQ API returned an invalid bot identity");
        server.verify();
    }

    private void expectLogin() {
        server.expect(requestTo(BASE + "get_login_info")).andRespond(withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"user_id\":123,\"nickname\":\"Test Bot\"}}",
                MediaType.APPLICATION_JSON));
    }

    private void expectForward(String... expected) {
        server.expect(requestTo(BASE + "send_group_forward_msg"))
                .andExpect(header("Authorization", "Bearer synthetic-token")).andExpect(request -> {
                    var body = body(request);
                    assertThat(body.path("group_id").asLong()).isEqualTo(42);
                    assertThat(body.path("messages").size()).isEqualTo(2);
                    StringBuilder text = new StringBuilder();
                    int images = 0;
                    for (var node : body.path("messages")) {
                        assertThat(node.path("type").asText()).isEqualTo("node");
                        assertThat(node.path("data").path("user_id").asText()).isEqualTo("123");
                        for (var segment : node.path("data").path("content")) {
                            if ("image".equals(segment.path("type").asText())) {
                                assertThat(segment.path("data").path("file").asText()).isEqualTo(IMAGE);
                                images++;
                            } else {
                                text.append(segment.path("data").path("text").asText());
                            }
                        }
                    }
                    assertThat(images).isEqualTo(1);
                    assertThat(text.toString()).contains(expected).doesNotContain("https://", "http://", "null");
                }).andRespond(withSuccess(SENT, MediaType.APPLICATION_JSON));
    }

    private com.fasterxml.jackson.databind.JsonNode body(org.springframework.http.client.ClientHttpRequest request)
            throws java.io.IOException {
        return new ObjectMapper().readTree(((MockClientHttpRequest) request).getBodyAsBytes());
    }
}

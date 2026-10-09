package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.ConsoleNotificationSender;
import com.twcodeart.tcgnews.notification.NotificationFormatter;
import com.twcodeart.tcgnews.notification.NotificationSender;
import com.twcodeart.tcgnews.notification.qq.QqNotificationException;
import com.twcodeart.tcgnews.notification.qq.QqNotificationSender;
import com.twcodeart.tcgnews.notification.qq.QqProperties;
import com.twcodeart.tcgnews.source.ws.WsCardRevealSource;
import com.twcodeart.tcgnews.source.ws.WsInformationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qq-api-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "rsshub.enabled=false",
        "qq.enabled=true", "qq.api-base-url=http://qq-api.example", "qq.test-group-id=42", "qq.access-token="
})
@AutoConfigureMockMvc
class QqNotificationApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private NewsItemRepository repository;
    @Autowired private QqProperties properties;
    @Autowired private NotificationSender defaultSender;
    @MockitoBean private QqNotificationSender qqSender;
    @MockitoBean private WsCardRevealSource cardSource;
    @MockitoBean private WsInformationSource informationSource;
    @MockitoSpyBean private NotificationFormatter formatter;

    @BeforeEach
    void resetDatabaseAndConfiguration() {
        repository.deleteAll();
        properties.setTestGroupId("42");
    }

    @Test
    void sendsOneTestMessageAndKeepsTheFetchPipelineOnConsole() throws Exception {
        assertThat(defaultSender).isInstanceOf(ConsoleNotificationSender.class);
        mvc.perform(post("/api/notifications/test")).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempted").value(1)).andExpect(jsonPath("$.sent").value(1))
                .andExpect(jsonPath("$.errors").isEmpty());
        verify(qqSender).send("[TCG News Bot QQ Test]\n今日のカード notification smoke test.");
        verifyNoInteractions(cardSource, informationSource, formatter);
    }

    @Test
    void sendsTheNewestThreeStoredRecordsByDefaultUsingTheExistingFormatterWithoutScraping() throws Exception {
        for (int number = 0; number < 7; number++) { repository.saveAndFlush(news(number)); }
        mvc.perform(post("/api/notifications/news")).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempted").value(3)).andExpect(jsonPath("$.sent").value(3));
        var messages = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(qqSender, times(3)).send(messages.capture());
        assertThat(messages.getAllValues().get(0)).contains("今日のカード テスト 6",
                "https://ws-tcg.com/information/test-6/", "https://ws-tcg.com/cards/test-6.png");
        assertThat(messages.getAllValues().get(1)).contains("今日のカード テスト 5");
        assertThat(messages.getAllValues().get(2)).contains("今日のカード テスト 4");
        verify(formatter, times(3)).format(any(NewsItem.class));
        assertThat(repository.count()).isEqualTo(7);
        verifyNoInteractions(cardSource, informationSource);
    }

    @Test
    void permitsAtMostFiveAndRejectsInvalidLimitsWithoutSending() throws Exception {
        for (int number = 0; number < 7; number++) { repository.saveAndFlush(news(number)); }
        for (String limit : new String[] {"0", "6", "999999999999", "invalid"}) {
            mvc.perform(post("/api/notifications/news").param("limit", limit)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(qqSender);
        mvc.perform(post("/api/notifications/news").param("limit", "5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(5));
        verify(qqSender, times(5)).send(any(String.class));
        verifyNoInteractions(cardSource, informationSource);
    }

    @Test
    void rejectsCallerSuppliedGroupsMessagesAndUrls() throws Exception {
        mvc.perform(post("/api/notifications/test").param("group_id", "99")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/news").param("groupId", "99")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"group_id\":99,\"message\":\"Override\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/news").param("apiBaseUrl", "http://other.example"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(qqSender, formatter, cardSource, informationSource);
    }

    @Test
    void reportsHttpFailureUnavailableAndTimeoutWithoutClaimingDelivery() throws Exception {
        var reasons = new QqNotificationException.Reason[] {
                QqNotificationException.Reason.API_FAILURE, QqNotificationException.Reason.UNAVAILABLE,
                QqNotificationException.Reason.TIMEOUT};
        int[] statuses = {502, 503, 504};
        for (int index = 0; index < reasons.length; index++) {
            doThrow(new QqNotificationException(reasons[index], "QQ API test failure"))
                    .when(qqSender).send(any(String.class));
            mvc.perform(post("/api/notifications/test")).andExpect(status().is(statuses[index]))
                    .andExpect(jsonPath("$.attempted").value(1)).andExpect(jsonPath("$.sent").value(0))
                    .andExpect(jsonPath("$.errors[0].reason").value(reasons[index].name()));
        }
    }

    @Test
    void emptyDatabaseSendsNothingAndMissingConfigurationReturns503() throws Exception {
        mvc.perform(post("/api/notifications/news")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(0));
        properties.setTestGroupId("");
        mvc.perform(post("/api/notifications/test")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("qq.test-group-id must be a positive numeric group ID"));
        verifyNoInteractions(qqSender, formatter, cardSource, informationSource);
    }

    @Test
    void manualPreviewPassesSeparateImageMetadataAndStillUsesTheSharedFormatter() throws Exception {
        repository.saveAndFlush(news(1));
        when(qqSender.acceptsImages()).thenReturn(true);
        mvc.perform(post("/api/notifications/news").param("limit", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1));
        verify(qqSender).send(any(String.class), eq("https://ws-tcg.com/cards/test-1.png"));
        verify(formatter).format(any(NewsItem.class));
        verifyNoInteractions(cardSource, informationSource);
    }

    @Test
    void previewsOnlyStoredOcgRecordsWithoutChangingTheConfiguredGroup() throws Exception {
        repository.saveAndFlush(new NewsItem(Game.YUGIOH_OCG, NewsCategory.CARD_REVEAL, "収録カード公開", null,
                null, java.time.LocalDate.of(2026,10,8), "https://pbs.twimg.com/media/synthetic-ocg.png",
                "https://x.com/YuGiOh_OCG_INFO/status/123", "f".repeat(64)));
        repository.saveAndFlush(news(1));
        mvc.perform(post("/api/notifications/news").param("game", "YUGIOH_OCG").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(1));
        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(qqSender).send(message.capture());
        assertThat(message.getValue()).contains("Yu-Gi-Oh! OCG", "収録カード公開", "Published date: 2026-10-08")
                .doesNotContain("Weiß Schwarz");
        verifyNoInteractions(cardSource, informationSource);
    }

    private NewsItem news(int number) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS, "今日のカード テスト " + number,
                null, null, "https://ws-tcg.com/cards/test-" + number + ".png",
                "https://ws-tcg.com/information/test-" + number + "/", String.format("%064x", number + 1));
    }
}

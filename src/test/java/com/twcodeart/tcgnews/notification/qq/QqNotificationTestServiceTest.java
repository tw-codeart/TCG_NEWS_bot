package com.twcodeart.tcgnews.notification.qq;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.NotificationFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QqNotificationTestServiceTest {

    private final QqProperties properties = new QqProperties();
    private final QqNotificationSender sender = mock(QqNotificationSender.class);
    private final NotificationFormatter formatter = mock(NotificationFormatter.class);
    private final NewsQueryService query = mock(NewsQueryService.class);
    private QqNotificationTestService service;

    @BeforeEach
    void configure() {
        properties.setEnabled(true);
        properties.setTestGroupId("42");
        ObjectProvider<QqNotificationSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        service = new QqNotificationTestService(properties, provider, formatter, query);
    }

    @Test
    void sendsOneTestMessageWithoutQueryingStoredNews() {
        assertThat(service.sendTest().sent()).isEqualTo(1);
        verify(sender).send("[TCG News Bot QQ Test]\n今日のカード notification smoke test.\nhttps://ws-tcg.com/");
        verifyNoInteractions(query, formatter);
    }

    @Test
    void boundsSendingEvenWhenTheQueryReturnsTooManyRecordsAndReusesFormatter() {
        List<NewsItem> items = IntStream.range(0, 8).mapToObj(this::news).toList();
        when(query.findLatestNews(5)).thenReturn(items);
        when(formatter.format(any(NewsItem.class))).thenAnswer(call -> ((NewsItem) call.getArgument(0)).getTitle());
        var result = service.sendStoredNews(5);
        assertThat(result.sent()).isEqualTo(5);
        verify(sender, times(5)).send(any(String.class));
        verify(formatter, times(5)).format(any(NewsItem.class));
        verify(formatter, never()).format(items.get(5));
    }

    @Test
    void rejectsInvalidLimitsAndDisabledSendingWithoutQueryingOrSending() {
        for (int limit : new int[] {0, -1, 6, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> service.sendStoredNews(limit)).isInstanceOf(IllegalArgumentException.class);
        }
        properties.setEnabled(false);
        assertThatThrownBy(() -> service.sendStoredNews(3)).isInstanceOf(QqNotificationException.class);
        verifyNoInteractions(query, formatter, sender);
    }

    @Test
    void stopsAfterFailureAndReportsTheAlreadySentCount() {
        when(query.findLatestNews(3)).thenReturn(List.of(news(0), news(1), news(2)));
        when(formatter.format(any(NewsItem.class))).thenAnswer(call -> ((NewsItem) call.getArgument(0)).getTitle());
        doThrow(new QqNotificationException(QqNotificationException.Reason.API_FAILURE, "QQ API returned HTTP 500"))
                .when(sender).send("Notice 1");
        var result = service.sendStoredNews(3);
        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        verify(sender, never()).send("Notice 2");
    }

    @Test
    void emptyDatabaseSendsNothingAndFormattingFailureIsReportedSafely() {
        when(query.findLatestNews(3)).thenReturn(List.of());
        assertThat(service.sendStoredNews(3).sent()).isZero();
        verifyNoInteractions(sender, formatter);
        when(query.findLatestNews(3)).thenReturn(List.of(news(0)));
        when(formatter.format(any(NewsItem.class))).thenThrow(new IllegalStateException("Internal details"));
        var result = service.sendStoredNews(3);
        assertThat(result.errors()).containsExactly(new QqNotificationTestService.Error(
                QqNotificationException.Reason.FORMATTING_FAILURE, "Notification formatting failed"));
        verifyNoInteractions(sender);
    }

    private NewsItem news(int index) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS, "Notice " + index, null, null,
                null, "https://ws-tcg.com/information/test-" + index + "/", String.format("%064x", index + 1));
    }
}

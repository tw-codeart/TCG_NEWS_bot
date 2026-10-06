package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.*;
import com.twcodeart.tcgnews.notification.qq.QqNotificationException;
import com.twcodeart.tcgnews.notification.qq.QqNotificationSender;
import com.twcodeart.tcgnews.source.NewsSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"rsshub.enabled=true", "rsshub.base-url=https://rsshub.example", "qq.enabled=true"})
@Import({NewsIngestionService.class, NewsDeduplicationPolicy.class, NewsNotificationService.class,
        PlainTextNotificationFormatter.class, ConsoleNotificationSender.class, RssHubProperties.class,
        RssHubQqNotificationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RssHubQqNotificationPipelineTest {

    @Autowired private NewsItemRepository news;
    @Autowired private SourceNotificationBootstrapRepository bootstraps;
    @Autowired private NewsIngestionService ingestion;
    @Autowired private NewsNotificationService notifications;
    @Autowired private ObjectProvider<RssHubQqNotificationService> rssHubNotifications;
    @Autowired private ObjectProvider<QqNotificationSender> qqSenders;
    @Autowired private RssHubProperties properties;
    @MockitoBean private QqNotificationSender qq;
    @MockitoSpyBean private PlainTextNotificationFormatter formatter;
    @MockitoSpyBean private ConsoleNotificationSender console;
    private WsRssHubFeedClient client;
    private WsRssHubNewsSource source;

    @BeforeEach
    void reset() {
        news.deleteAll();
        bootstraps.deleteAll();
        properties.setNotificationBatchLimit(3);
        client = mock(WsRssHubFeedClient.class);
        source = new WsRssHubNewsSource(client, new WsRssHubFeedParser());
    }

    @Test
    void bootstrapsHistoryThenSendsOnlyANewItemAfterRecreatingTheNotificationService() throws Exception {
        bootstrap();
        var marker = bootstraps.findById(WsRssHubNewsSource.SOURCE_ID).orElseThrow();
        assertThat(marker.getInitializedAt()).isNotNull();
        // A fresh service consults persisted state, rather than an in-memory first-run flag.
        var restarted = new RssHubQqNotificationService(bootstraps, properties, qqSenders, notifications);
        ObjectProvider<RssHubQqNotificationService> restartedProvider = mock(ObjectProvider.class);
        when(restartedProvider.getIfAvailable()).thenReturn(restarted);
        when(client.fetch()).thenReturn(fixture("ws-rsshub-new.xml"));
        var fetch = new NewsFetchService(List.of(source), ingestion, notifications, restartedProvider);

        assertThat(fetch.fetchAll().saved()).isEqualTo(1);
        var item = news.findAll().stream().filter(record -> record.getSourceUrl().endsWith("/9100"))
                .findFirst().orElseThrow();
        assertThat(item.getPublishedAt()).isEqualTo(Instant.parse("2026-10-06T10:00:00Z"));
        assertThat(item.getTitle()).isEqualTo("今日のカード 新しいテスト公開");
        assertThat(item.getImageUrl()).isEqualTo("https://pbs.twimg.com/media/new-card.png");
        assertThat(item.getCreatedAt()).isNotNull();
        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(qq).send(message.capture());
        assertThat(message.getValue()).contains(item.getTitle(), item.getSourceUrl(), item.getImageUrl());
        var formatted = org.mockito.ArgumentCaptor.forClass(NewsItem.class);
        verify(formatter).format(formatted.capture());
        assertThat(formatted.getValue().getId()).isEqualTo(item.getId());
        assertThat(fetch.fetchAll().saved()).isZero();
        verifyNoMoreInteractions(qq);
        assertThat(news.count()).isEqualTo(6);
    }

    @Test
    void keepsAllImagesDistinctButDefaultsToThreeNotificationsAndNeverReplaysTheRest() throws Exception {
        bootstrap();
        when(client.fetch()).thenReturn(fixture("ws-rsshub-new-multiple.xml"));
        assertThat(fetch(source).saved()).isEqualTo(7);
        var items = news.findAll().stream().filter(item -> item.getSourceUrl().endsWith("/9200")).toList();
        assertThat(items).hasSize(7).extracting(NewsItem::getContentHash).doesNotHaveDuplicates();
        assertThat(items).extracting(NewsItem::getImageUrl).doesNotHaveDuplicates();
        assertThat(items).extracting(NewsItem::getPublishedAt).containsOnly(Instant.parse("2026-10-06T10:00:00Z"));
        verify(qq, times(3)).send(any(String.class));
        verify(formatter, times(3)).format(any(NewsItem.class));
        var repeated = fetch(source);
        assertThat(repeated.saved()).isZero();
        assertThat(repeated.duplicates()).isEqualTo(7);
        verifyNoMoreInteractions(qq);
        assertThat(news.count()).isEqualTo(12);
    }

    @Test
    void configuredMaximumSendsOnlyFiveMessages() throws Exception {
        bootstrap();
        properties.setNotificationBatchLimit(5);
        when(client.fetch()).thenReturn(fixture("ws-rsshub-new-multiple.xml"));
        assertThat(fetch(source).saved()).isEqualTo(7);
        verify(qq, times(5)).send(any(String.class));
    }

    @Test
    void qqFailureLeavesAllNewRowsCommittedAndDoesNotRetryDuplicates() throws Exception {
        bootstrap();
        when(client.fetch()).thenReturn(fixture("ws-rsshub-new-multiple.xml"));
        doThrow(new QqNotificationException(QqNotificationException.Reason.UNAVAILABLE, "QQ API is unavailable"))
                .when(qq).send(any(String.class));
        var result = fetch(source);
        assertThat(result.saved()).isEqualTo(7);
        assertThat(result.successfulSources()).isEqualTo(1);
        assertThat(news.count()).isEqualTo(12);
        verify(qq, times(3)).send(any(String.class));
        assertThat(fetch(source).saved()).isZero();
        verifyNoMoreInteractions(qq);
    }

    @Test
    void rssHubFailureDoesNotPreventAWebsiteSourceFromSavingAndNotifying() throws Exception {
        when(client.fetch()).thenThrow(new IOException("Feed unavailable"));
        var website = mock(NewsSource.class);
        when(website.getSourceId()).thenReturn("test-website");
        when(website.fetchNews()).thenReturn(List.of(new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS,
                "Website notice", null, null, null, "https://ws-tcg.com/information/test/", "f".repeat(64))));
        var result = fetch(source, website);
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.successfulSources()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(news.count()).isEqualTo(1);
        verify(console).send(any(String.class));
        verifyNoInteractions(qq);
        assertThat(bootstraps.count()).isZero();
    }

    @Test
    void emptyFeedWaitsForANonemptyBaselineAndHistoricalDuplicatesSendNothing() throws Exception {
        when(client.fetch()).thenReturn(fixture("ws-rsshub-empty.xml"));
        assertThat(fetch(source).saved()).isZero();
        assertThat(bootstraps.count()).isZero();
        bootstrap();
        var result = fetch(source);
        assertThat(result.saved()).isZero();
        assertThat(result.duplicates()).isEqualTo(6);
        verifyNoInteractions(qq, formatter);
    }

    @Test
    void partialHistoricalSaveDoesNotCompleteBootstrapOrSendNotifications() throws Exception {
        var history = new WsRssHubFeedParser().parse(fixture("ws-rsshub.xml"));
        var first = news.saveAndFlush(history.get(0));
        var partial = mock(NewsIngestionService.class);
        when(partial.fetchAndSaveWithSummary(source)).thenThrow(new NewsIngestionService.IngestionFailure(
                new NewsIngestionService.IngestionResult(6, List.of(first), 0), new IllegalStateException("Storage failed")));
        assertThat(new NewsFetchService(List.of(source), partial, notifications, rssHubNotifications)
                .fetchAll().saved()).isEqualTo(1);
        assertThat(bootstraps.count()).isZero();
        when(client.fetch()).thenReturn(fixture("ws-rsshub.xml"));
        assertThat(fetch(source).saved()).isEqualTo(4);
        assertThat(bootstraps.count()).isEqualTo(1);
        verifyNoInteractions(qq, formatter);
    }

    @Test
    void aDisabledQqSenderSkipsNewItemsWithoutReplayingThemLater() throws Exception {
        bootstrap();
        ObjectProvider<QqNotificationSender> disabled = mock(ObjectProvider.class);
        var router = new RssHubQqNotificationService(bootstraps, properties, disabled, notifications);
        ObjectProvider<RssHubQqNotificationService> routerProvider = mock(ObjectProvider.class);
        when(routerProvider.getIfAvailable()).thenReturn(router);
        when(client.fetch()).thenReturn(fixture("ws-rsshub-new.xml"));
        assertThat(new NewsFetchService(List.of(source), ingestion, notifications, routerProvider).fetchAll().saved())
                .isEqualTo(1);
        assertThat(fetch(source).saved()).isZero();
        verifyNoInteractions(qq, formatter);
    }

    private void bootstrap() throws Exception {
        when(client.fetch()).thenReturn(fixture("ws-rsshub.xml"));
        fetch(source);
        assertThat(news.count()).isEqualTo(5);
        assertThat(bootstraps.count()).isEqualTo(1);
        verifyNoInteractions(qq, formatter);
    }

    private byte[] fixture(String name) throws Exception {
        return WsRssHubFeedParserTest.fixture(name);
    }

    private NewsFetchSummary fetch(NewsSource... sources) {
        return new NewsFetchService(List.of(sources), ingestion, notifications, rssHubNotifications).fetchAll();
    }
}

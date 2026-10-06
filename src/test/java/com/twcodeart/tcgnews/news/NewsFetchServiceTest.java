package com.twcodeart.tcgnews.news;

import com.twcodeart.tcgnews.notification.NewsNotificationService;
import com.twcodeart.tcgnews.notification.NotificationFormatter;
import com.twcodeart.tcgnews.notification.NotificationSender;
import com.twcodeart.tcgnews.source.NewsSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsFetchServiceTest {

    @Test
    void manualAndSelectedFetchesDoNotCollectConcurrently() throws Exception {
        var source = mock(NewsSource.class);
        when(source.getSourceId()).thenReturn("rsshub-test");
        var ingestion = mock(NewsIngestionService.class);
        var firstEntered = new java.util.concurrent.CountDownLatch(1);
        var releaseFirst = new java.util.concurrent.CountDownLatch(1);
        var secondStarted = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(ingestion.fetchAndSaveWithSummary(source)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstEntered.countDown();
                if (!releaseFirst.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test release timed out");
                }
            }
            return new NewsIngestionService.IngestionResult(0, List.of(), 0);
        });
        var fetch = new NewsFetchService(List.of(source), ingestion, mock(NewsNotificationService.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(fetch::fetchAll);
            assertThat(firstEntered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return fetch.fetchSource("rsshub-test");
            });
            assertThat(secondStarted.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> second.get(100, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThat(calls.get()).isEqualTo(1);
            releaseFirst.countDown();
            first.get(3, java.util.concurrent.TimeUnit.SECONDS);
            second.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void fetchSourceDoesNotInvokeOtherConfiguredSources() throws Exception {
        var rssHub = mock(NewsSource.class);
        var website = mock(NewsSource.class);
        when(rssHub.getSourceId()).thenReturn("rsshub-test");
        when(website.getSourceId()).thenReturn("website-test");
        var ingestion = mock(NewsIngestionService.class);
        when(ingestion.fetchAndSaveWithSummary(rssHub))
                .thenReturn(new NewsIngestionService.IngestionResult(1, List.of(), 1));
        var notifications = mock(NewsNotificationService.class);
        var fetch = new NewsFetchService(List.of(website, rssHub), ingestion, notifications,
                mock(org.springframework.beans.factory.ObjectProvider.class));
        assertThat(fetch.fetchSource("rsshub-test").duplicates()).isEqualTo(1);
        verify(ingestion).fetchAndSaveWithSummary(rssHub);
        verify(ingestion, org.mockito.Mockito.never()).fetchAndSaveWithSummary(website);
    }

    @Test
    void includesPartialSavesAndContinuesWithAnotherSourceAfterPersistenceFailure() throws Exception {
        NewsSource failed = mock(NewsSource.class);
        NewsSource valid = mock(NewsSource.class);
        NewsIngestionService ingestion = mock(NewsIngestionService.class);
        when(failed.getSourceId()).thenReturn("failed");
        NewsItem first = news("first", "a".repeat(64));
        NewsItem second = news("second", "b".repeat(64));
        var partial = new NewsIngestionService.IngestionResult(3, List.of(first), 0);
        when(ingestion.fetchAndSaveWithSummary(failed)).thenThrow(
                new NewsIngestionService.IngestionFailure(partial, new IllegalStateException("Storage failure")));
        when(ingestion.fetchAndSaveWithSummary(valid)).thenReturn(
                new NewsIngestionService.IngestionResult(1, List.of(second), 0));

        NotificationFormatter formatter = mock(NotificationFormatter.class);
        NotificationSender sender = mock(NotificationSender.class);
        when(formatter.format(first)).thenReturn("First saved message");
        when(formatter.format(second)).thenReturn("Second saved message");
        var notifications = new NewsNotificationService(formatter, sender);
        var summary = new NewsFetchService(List.of(failed, valid), ingestion, notifications,
                mock(org.springframework.beans.factory.ObjectProvider.class)).fetchAll();
        assertThat(summary.fetched()).isEqualTo(4);
        assertThat(summary.saved()).isEqualTo(2);
        assertThat(summary.duplicates()).isZero();
        assertThat(summary.unprocessed()).isEqualTo(2);
        assertThat(summary.successfulSources()).isEqualTo(1);
        assertThat(summary.allSourcesFailed()).isFalse();
        assertThat(summary.errors()).containsExactly(new NewsFetchSummary.SourceError("failed", "News persistence failed"));
        verify(ingestion).fetchAndSaveWithSummary(valid);
        verify(sender).send("First saved message");
        verify(sender).send("Second saved message");
    }

    private NewsItem news(String title, String hash) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS, title, null, null,
                null, "https://ws-tcg.com/information/" + title + "/", hash);
    }
}

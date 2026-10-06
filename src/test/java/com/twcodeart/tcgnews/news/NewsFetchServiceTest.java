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
        var summary = new NewsFetchService(List.of(failed, valid), ingestion, notifications).fetchAll();
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

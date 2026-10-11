package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.NewsFetchService;
import com.twcodeart.tcgnews.news.NewsFetchSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class RssHubPollingServiceTest {

    @Test
    void selectsBothConfiguredRssHubSourcesInOneBoundedFetchWhenOcgIsEnabled() {
        var fetch = mock(NewsFetchService.class);
        var properties = new RssHubProperties();
        properties.setOcgEnabled(true);
        var ids = List.of(WsRssHubNewsSource.SOURCE_ID, com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource.SOURCE_ID);
        when(fetch.fetchSourcesByIds(ids)).thenReturn(new NewsFetchSummary(13,13,0,0,2,List.of()));
        new RssHubPollingService(fetch, properties, new RssHubPollingStatus()).poll();
        verify(fetch).fetchSourcesByIds(ids);
        verify(fetch, never()).fetchAll();
    }

    @Test
    void selectsOnlyTheRssHubSourceAndContinuesAfterAnUnexpectedFailure() {
        var fetch = mock(NewsFetchService.class);
        when(fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID)).thenThrow(new IllegalStateException("Temporary failure"))
                .thenReturn(new NewsFetchSummary(10, 0, 10, 0, 1, List.of()));
        var polling = new RssHubPollingService(fetch, new RssHubProperties(), new RssHubPollingStatus());
        polling.poll();
        polling.poll();
        verify(fetch, times(2)).fetchSource(WsRssHubNewsSource.SOURCE_ID);
        verify(fetch, never()).fetchAll();
    }

    @Test
    void recordsTheLastFailureAndRecoveryWithoutLeakingTheExceptionMessage() {
        var fetch = mock(NewsFetchService.class);
        when(fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID)).thenThrow(new IllegalStateException("Private details"))
                .thenReturn(new NewsFetchSummary(10, 2, 8, 0, 1, List.of()));
        var status = new RssHubPollingStatus();
        var service = new RssHubPollingService(fetch, new RssHubProperties(), status);
        service.poll();
        org.assertj.core.api.Assertions.assertThat(status.snapshot().lastFetchSuccessful()).isFalse();
        org.assertj.core.api.Assertions.assertThat(status.snapshot().errorId()).isNotBlank();
        service.poll();
        org.assertj.core.api.Assertions.assertThat(status.snapshot().lastFetchSuccessful()).isTrue();
        org.assertj.core.api.Assertions.assertThat(status.snapshot().saved()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(status.snapshot().errorId()).isNull();
    }
}

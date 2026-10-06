package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.NewsFetchService;
import com.twcodeart.tcgnews.news.NewsFetchSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class RssHubPollingServiceTest {

    @Test
    void selectsOnlyTheRssHubSourceAndContinuesAfterAnUnexpectedFailure() {
        var fetch = mock(NewsFetchService.class);
        when(fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID)).thenThrow(new IllegalStateException("Temporary failure"))
                .thenReturn(new NewsFetchSummary(10, 0, 10, 0, 1, List.of()));
        var polling = new RssHubPollingService(fetch, new RssHubProperties());
        polling.poll();
        polling.poll();
        verify(fetch, times(2)).fetchSource(WsRssHubNewsSource.SOURCE_ID);
        verify(fetch, never()).fetchAll();
    }
}

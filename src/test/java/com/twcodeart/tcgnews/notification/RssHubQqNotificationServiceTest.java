package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsIngestionService;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.source.ws.RssHubProperties;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.mockito.Mockito.*;

class RssHubQqNotificationServiceTest {

    @Test
    void skipsNotificationsIfBootstrapStateCannotBeReadOrWritten() {
        var repository = mock(SourceNotificationBootstrapRepository.class);
        var notifications = mock(NewsNotificationService.class);
        var service = new RssHubQqNotificationService(repository, new RssHubProperties(),
                mock(ObjectProvider.class), notifications);
        var item = new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.CARD_REVEAL, "今日のカード", null,
                null, "https://pbs.twimg.com/media/test.png", "https://x.com/wstcg/status/1", "a".repeat(64));
        var result = new NewsIngestionService.IngestionResult(1, List.of(item), 0);
        when(repository.existsById(WsRssHubNewsSource.SOURCE_ID))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));
        service.notifySavedItems(result, true);
        verifyNoInteractions(notifications);
        reset(repository);
        doThrow(new DataAccessResourceFailureException("Database unavailable"))
                .when(repository).saveAndFlush(any(SourceNotificationBootstrap.class));
        service.notifySavedItems(result, true);
        verifyNoInteractions(notifications);
    }
}

package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class NewsNotificationServiceTest {

    @Test
    void continuesAfterFormattingFailureAndPassesTheFormatterOutputToTheSender() {
        NotificationFormatter formatter = mock(NotificationFormatter.class);
        NotificationSender sender = mock(NotificationSender.class);
        NewsItem first = news("first", "a".repeat(64));
        NewsItem second = news("second", "b".repeat(64));
        when(formatter.format(first)).thenThrow(new IllegalStateException("Formatting failed"));
        when(formatter.format(second)).thenReturn("Existing formatter output");

        new NewsNotificationService(formatter, sender).notifyNewItems(List.of(first, second));

        verify(formatter).format(first);
        verify(formatter).format(second);
        verify(sender).send("Existing formatter output");
        verify(sender).acceptsImages();
        verifyNoMoreInteractions(sender);
    }

    private NewsItem news(String title, String hash) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS, title, null,
                null, null, "https://ws-tcg.com/information/" + title + "/", hash);
    }
}

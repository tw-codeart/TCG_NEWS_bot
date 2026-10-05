package com.twcodeart.tcgnews.news;

import com.twcodeart.tcgnews.source.NewsSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class NewsIngestionSummaryTest {

    @Test
    void retainsSavedCountsAndTheCauseWhenPersistenceStopsMidBatch() throws Exception {
        NewsItemRepository repository = mock(NewsItemRepository.class);
        NewsDeduplicationPolicy policy = mock(NewsDeduplicationPolicy.class);
        NewsSource source = mock(NewsSource.class);
        NewsItem first = news("first", "a".repeat(64));
        NewsItem second = news("second", "b".repeat(64));
        NewsItem third = news("third", "c".repeat(64));
        when(source.fetchNews()).thenReturn(List.of(first, second, third));
        when(repository.saveAndFlush(first)).thenReturn(first);
        var cause = new DataIntegrityViolationException("Invalid record");
        when(repository.saveAndFlush(second)).thenThrow(cause);
        var service = new NewsIngestionService(repository, policy);

        var failure = catchThrowableOfType(() -> service.fetchAndSaveWithSummary(source),
                NewsIngestionService.IngestionFailure.class);
        assertThat(failure).hasCause(cause);
        assertThat(failure.getResult().fetched()).isEqualTo(3);
        assertThat(failure.getResult().savedItems()).containsExactly(first);
        assertThat(failure.getResult().duplicates()).isZero();
        assertThat(failure.getResult().unprocessed()).isEqualTo(2);
        verify(repository, never()).saveAndFlush(third);
    }

    private NewsItem news(String title, String hash) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS, title, null, null,
                null, "https://ws-tcg.com/information/" + title + "/", hash);
    }
}

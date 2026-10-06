package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsDeduplicationPolicy;
import com.twcodeart.tcgnews.news.NewsFetchService;
import com.twcodeart.tcgnews.news.NewsFetchSummary;
import com.twcodeart.tcgnews.news.NewsIngestionService;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.news.NewsItemRepository;
import com.twcodeart.tcgnews.source.NewsSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({NewsIngestionService.class, NewsDeduplicationPolicy.class, NewsNotificationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationPipelineTest {

    @Autowired private NewsItemRepository repository;
    @Autowired private NewsIngestionService ingestion;
    @Autowired private NewsNotificationService notifications;
    @MockitoBean private NotificationFormatter formatter;
    @MockitoBean private NotificationSender sender;

    @BeforeEach
    void resetDatabaseAndUseTheInjectedFormatter() {
        repository.deleteAll();
        when(formatter.format(any(NewsItem.class)))
                .thenAnswer(call -> message(call.getArgument(0)));
    }

    @Test
    void notifiesANewItemAfterSavingAndSendsNothingForTheRepeatedFetch() throws Exception {
        NewsSource source = source();
        when(source.fetchNews()).thenAnswer(call -> List.of(news(0)));
        NewsFetchService fetch = new NewsFetchService(List.of(source), ingestion, notifications);

        assertThat(fetch.fetchAll().saved()).isEqualTo(1);
        NewsItem stored = repository.findAll().get(0);
        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getCreatedAt()).isNotNull();
        // Capture the actual persisted object handed to the existing formatter.
        var formatted = org.mockito.ArgumentCaptor.forClass(NewsItem.class);
        verify(formatter).format(formatted.capture());
        assertThat(formatted.getValue().getId()).isEqualTo(stored.getId());
        verify(sender).send(message(stored));

        NewsFetchSummary repeated = fetch.fetchAll();
        assertThat(repeated.saved()).isZero();
        assertThat(repeated.duplicates()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        verifyNoMoreInteractions(formatter, sender);
    }

    @Test
    void sendsExactlyThreeMessagesForTwentyItemsWithSeventeenAlreadyStored() throws Exception {
        repository.saveAllAndFlush(IntStream.range(0, 17).mapToObj(this::news).toList());
        NewsSource source = source();
        when(source.fetchNews()).thenReturn(IntStream.range(0, 20).mapToObj(this::news).toList());

        NewsFetchSummary result = fetch(source);

        assertThat(result.fetched()).isEqualTo(20);
        assertThat(result.saved()).isEqualTo(3);
        assertThat(result.duplicates()).isEqualTo(17);
        assertThat(repository.count()).isEqualTo(20);
        verify(formatter, times(3)).format(any(NewsItem.class));
        var sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sender, times(3)).send(sent.capture());
        assertThat(sent.getAllValues()).containsExactly(message(news(17)), message(news(18)), message(news(19)));
    }

    @Test
    void sendsNothingForAnEmptyFetch() throws Exception {
        NewsSource source = source();
        when(source.fetchNews()).thenReturn(List.of());

        assertThat(fetch(source).saved()).isZero();
        assertThat(repository.count()).isZero();
        verifyNoInteractions(formatter, sender);
    }

    @Test
    void keepsCommittedNewsAndContinuesSendingAfterASenderFailure() throws Exception {
        NewsSource source = source();
        when(source.fetchNews()).thenReturn(List.of(news(0), news(1)));
        doThrow(new IllegalStateException("Console transport failed")).when(sender).send(message(news(0)));

        NewsFetchSummary result = fetch(source);

        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.successfulSources()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        // No surrounding test transaction: these rows were committed before sending.
        assertThat(repository.findAll()).hasSize(2).allSatisfy(item -> {
            assertThat(item.getId()).isNotNull();
            assertThat(item.getCreatedAt()).isNotNull();
        });
        verify(sender).send(message(news(0)));
        verify(sender).send(message(news(1)));
    }

    private NewsFetchSummary fetch(NewsSource source) {
        return new NewsFetchService(List.of(source), ingestion, notifications).fetchAll();
    }

    private NewsSource source() {
        NewsSource source = mock(NewsSource.class);
        when(source.getSourceId()).thenReturn("test-notifications");
        return source;
    }

    private NewsItem news(int number) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS, "Test notice " + number,
                null, null, null, "https://ws-tcg.com/information/test-" + number + "/",
                String.format("%064x", number + 1));
    }

    private String message(NewsItem item) {
        return "Formatted by the injected formatter: " + item.getSourceUrl();
    }
}

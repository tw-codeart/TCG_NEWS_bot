package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.NewsDeduplicationPolicy;
import com.twcodeart.tcgnews.news.NewsIngestionService;
import com.twcodeart.tcgnews.news.NewsItemRepository;
import com.twcodeart.tcgnews.notification.PlainTextNotificationFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({NewsIngestionService.class, NewsDeduplicationPolicy.class})
class WsRssHubIngestionTest {
    @Autowired private NewsIngestionService ingestion;
    @Autowired private NewsItemRepository repository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void preservesSeparateImagesPersistsTimestampsAndDeduplicatesRepeatedFetches() throws Exception {
        var client = mock(WsRssHubFeedClient.class);
        when(client.fetch()).thenReturn(WsRssHubFeedParserTest.fixture("ws-rsshub.xml"));
        var source = new WsRssHubNewsSource(client, new WsRssHubFeedParser());
        var first = ingestion.fetchAndSaveWithSummary(source);
        assertThat(first.fetched()).isEqualTo(6);
        assertThat(first.savedItems()).hasSize(5);
        assertThat(first.duplicates()).isEqualTo(1);
        var second = ingestion.fetchAndSaveWithSummary(source);
        assertThat(second.fetched()).isEqualTo(6);
        assertThat(second.savedItems()).isEmpty();
        assertThat(second.duplicates()).isEqualTo(6);
        entityManager.clear();
        var stored = repository.findAll();
        assertThat(stored).hasSize(5).allSatisfy(item -> {
            assertThat(item.getCreatedAt()).isNotNull();
            assertThat(item.getProductName()).isNull();
            assertThat(item.getRevealDate()).isNull();
        });
        assertThat(stored.stream().filter(item -> item.getSourceUrl().endsWith("/200"))).hasSize(2);
        var firstPost = stored.stream().filter(item -> item.getSourceUrl().endsWith("/100")).findFirst().orElseThrow();
        assertThat(firstPost.getPublishedAt()).isEqualTo(Instant.parse("2026-10-05T23:30:00Z"));
        assertThat(new PlainTextNotificationFormatter().format(firstPost))
                .contains("今日のカード テスト公開", firstPost.getSourceUrl(), firstPost.getImageUrl())
                .doesNotContain("null", "Reveal date:");
    }
}

package com.twcodeart.tcgnews.news;

import com.twcodeart.tcgnews.product.Product;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.ws.WsCardRevealsParser;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
class NewsIngestionServiceTest {

    @Autowired
    private NewsItemRepository repository;

    @Test
    void savesSeparateSameDayCardsAndSkipsDuplicatesWithinAndAcrossFetches() throws Exception {
        Product product = new Product(Game.WEISS_SCHWARZ, "Test product", "https://ws-tcg.com/products/test/");
        List<NewsItem> parsed;
        List<NewsItem> repeated;
        try (InputStream fixture = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/ws-card-reveals-archive.html"))) {
            var document = Jsoup.parse(fixture, "UTF-8", product.getDetailUrl());
            var parser = new WsCardRevealsParser();
            parsed = parser.parse(document, product);
            repeated = parser.parse(document, product);
        }
        NewsSource source = mock(NewsSource.class);
        when(source.getSourceId()).thenReturn("test-card-reveals");
        when(source.fetchNews()).thenReturn(parsed).thenReturn(repeated);
        NewsIngestionService service = new NewsIngestionService(repository);

        List<NewsItem> saved = service.fetchAndSave(source);
        assertThat(saved).hasSize(3);
        assertThat(saved.stream().filter(item -> item.getRevealDate().equals(LocalDate.of(2026, 9, 29))))
                .hasSize(2);
        assertThat(saved).allSatisfy(item -> {
            assertThat(item.getId()).isNotNull();
            assertThat(item.getCreatedAt()).isNotNull();
            assertThat(item.getGame()).isEqualTo(Game.WEISS_SCHWARZ);
            assertThat(item.getCategory()).isEqualTo(NewsCategory.CARD_REVEAL);
            assertThat(item.getProductName()).isEqualTo(product.getProductName());
            assertThat(item.getSourceUrl()).isEqualTo(product.getDetailUrl());
            assertThat(item.getImageUrl()).isNotBlank();
            assertThat(item.getContentHash()).matches("[0-9a-f]{64}");
        });
        assertThat(service.fetchAndSave(source)).isEmpty();
        assertThat(repository.count()).isEqualTo(3);
    }
}

package com.twcodeart.tcgnews.news;

import com.twcodeart.tcgnews.product.Product;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.ws.WsCardRevealsParser;
import com.twcodeart.tcgnews.source.ws.WsInformationPageClient;
import com.twcodeart.tcgnews.source.ws.WsInformationParser;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({NewsIngestionService.class, NewsDeduplicationPolicy.class})
class NewsIngestionServiceTest {

    @Autowired
    private NewsItemRepository repository;

    @Autowired
    private NewsIngestionService service;

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

    @Test
    void skipsTheSameSourceAnnouncementWhenThumbnailAndHashChange() throws Exception {
        String url = "https://ws-tcg.com/products/test/";
        LocalDate date = LocalDate.of(2026, 9, 29);
        NewsItem first = news(url, "Test product announcement", date, null, "a".repeat(64));
        NewsItem updatedThumbnail = news(url, "Test product announcement", date,
                "https://ws-tcg.com/banner.jpg", "b".repeat(64));

        assertThat(service.fetchAndSave(sourceReturning(List.of(first)))).hasSize(1);
        assertThat(service.fetchAndSave(sourceReturning(List.of(updatedThumbnail)))).isEmpty();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void skipsTheSameCardImageAndDateFromDifferentPagesWithinAndAcrossFetches() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 29);
        String image = "https://ws-tcg.com/cards/one.png";
        NewsItem first = reveal("https://ws-tcg.com/products/first/", date, image, "a".repeat(64));
        NewsItem repeated = reveal("https://ws-tcg.com/products/second/", date, image, "b".repeat(64));

        assertThat(service.fetchAndSave(sourceReturning(List.of(first, repeated)))).hasSize(1);
        NewsItem laterFetch = reveal("https://ws-tcg.com/products/third/", date, image, "c".repeat(64));
        assertThat(service.fetchAndSave(sourceReturning(List.of(laterFetch)))).isEmpty();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void skipsIdenticalContentHashesEvenWhenOtherIdentifiersDiffer() throws Exception {
        String hash = "a".repeat(64);
        NewsItem first = news("https://ws-tcg.com/information/one/", "First notice", null, null, hash);
        NewsItem repeated = news("https://ws-tcg.com/information/two/", "Changed title", null, null, hash);

        assertThat(service.fetchAndSave(sourceReturning(List.of(first, repeated)))).hasSize(1);
        NewsItem laterFetch = news("https://ws-tcg.com/information/three/", "Another title", null, null, hash);
        assertThat(service.fetchAndSave(sourceReturning(List.of(laterFetch)))).isEmpty();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void savesInformationFixtureOnceAndSkipsAFreshRepeatedFetch() throws Exception {
        assertThat(service.fetchAndSave(sourceReturning(informationFixture()))).hasSize(6);
        assertThat(service.fetchAndSave(sourceReturning(informationFixture()))).isEmpty();
        assertThat(repository.count()).isEqualTo(6);
    }

    @Test
    void preservesSimilarTitlesWithSharedProductUrlDateAndBanner() throws Exception {
        String url = "https://ws-tcg.com/products/test/";
        String banner = "https://ws-tcg.com/banner.jpg";
        LocalDate date = LocalDate.of(2026, 9, 29);
        NewsItem first = news(url, "Test product announced", date, banner, "a".repeat(64));
        NewsItem second = news(url, "Test product signed cards announced", date, banner, "b".repeat(64));
        NewsItem third = news("https://ws-tcg.com/products/other/", first.getTitle(), date, banner, "c".repeat(64));

        assertThat(service.fetchAndSave(sourceReturning(List.of(first, second, third)))).hasSize(3);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void preservesTheSameCardImageOnDifferentRevealDates() throws Exception {
        String url = "https://ws-tcg.com/products/test/";
        String image = "https://ws-tcg.com/cards/one.png";
        NewsItem first = reveal(url, LocalDate.of(2026, 9, 28), image, "a".repeat(64));
        NewsItem second = reveal(url, LocalDate.of(2026, 9, 29), image, "b".repeat(64));

        assertThat(service.fetchAndSave(sourceReturning(List.of(first, second)))).hasSize(2);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void matchesMissingPublicationDatesWithoutMatchingUnrelatedNews() throws Exception {
        String url = "https://ws-tcg.com/information/test/";
        NewsItem first = news(url, "Official notice", null, null, "a".repeat(64));
        NewsItem changedImage = news(url, "Official notice", null, "https://ws-tcg.com/banner.jpg", "b".repeat(64));
        NewsItem otherNotice = news(url, "Different notice", null, null, "c".repeat(64));

        assertThat(service.fetchAndSave(sourceReturning(List.of(first)))).hasSize(1);
        assertThat(service.fetchAndSave(sourceReturning(List.of(changedImage, otherNotice)))).hasSize(1);
        assertThat(repository.count()).isEqualTo(2);
    }

    private NewsSource sourceReturning(List<NewsItem> items) throws Exception {
        NewsSource source = mock(NewsSource.class);
        when(source.getSourceId()).thenReturn("test-news");
        when(source.fetchNews()).thenReturn(items);
        return source;
    }

    private NewsItem news(String sourceUrl, String title, LocalDate date, String imageUrl, String hash) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS, title, "Test product",
                null, date, imageUrl, sourceUrl, hash);
    }

    private NewsItem reveal(String sourceUrl, LocalDate date, String imageUrl, String hash) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.CARD_REVEAL, null, "Test product",
                date, imageUrl, sourceUrl, hash);
    }

    private List<NewsItem> informationFixture() throws Exception {
        try (InputStream fixture = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/ws-information.html"))) {
            return new WsInformationParser().parse(
                    Jsoup.parse(fixture, "UTF-8", WsInformationPageClient.INFORMATION_URL));
        }
    }
}

package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class WsInformationParserTest {

    private final WsInformationParser parser = new WsInformationParser();

    @Test
    void parsesProductNewsWithTitleDateThumbnailAndExplicitProductName() throws IOException {
        NewsItem item = fixtureItems().get(0);

        assertThat(item.getGame()).isEqualTo(Game.WEISS_SCHWARZ);
        assertThat(item.getCategory()).isEqualTo(NewsCategory.PRODUCT_NEWS);
        assertThat(item.getTitle()).isEqualTo("トライアルデッキ［Test Product］公開");
        assertThat(item.getSourceUrl()).isEqualTo("https://ws-tcg.com/products/test/");
        assertThat(item.getPublishedDate()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(item.getRevealDate()).isNull();
        assertThat(item.getProductName()).isEqualTo("Test Product");
        assertThat(item.getImageUrl()).isEqualTo("https://ws-tcg.com/wordpress/wp-content/uploads/test/banner.jpg");
        assertThat(item.getContentHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void keepsValidEntriesAndOptionalNullsWhileSkippingMalformedEntries() throws IOException {
        List<NewsItem> items = fixtureItems();
        assertThat(items).hasSize(6);
        assertThat(items).extracting(NewsItem::getCategory).containsExactly(
                NewsCategory.PRODUCT_NEWS, NewsCategory.CARD_LIST_NEWS, NewsCategory.NEWS,
                NewsCategory.PRODUCT_NEWS, NewsCategory.NEWS, NewsCategory.PRODUCT_NEWS);
        assertThat(items.get(1).getSourceUrl())
                .isEqualTo("https://ws-tcg.com/cardlist/search/?expansion=123&keyword=test");
        assertThat(items.get(1).getImageUrl()).isNull();
        assertThat(items.get(2).getPublishedDate()).isNull();
        assertThat(items.get(2).getImageUrl()).isNull();
        assertThat(items.get(2).getProductName()).isNull();
        assertThat(items.get(3).getProductName()).isNull();
        assertThat(items.get(3).getImageUrl()).isNull();
        assertThat(items.get(3).getPublishedDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(items.get(4).getPublishedDate()).isNull();
        assertThat(items.get(4).getImageUrl()).isNull();
        assertThat(items.get(0).getSourceUrl()).isEqualTo(items.get(5).getSourceUrl());
        assertThat(items.get(0).getContentHash()).isNotEqualTo(items.get(5).getContentHash());
        assertThat(items).allSatisfy(item -> assertThat(item.getRevealDate()).isNull());
    }

    @Test
    void doesNotFailThePageWhenItsOnlyIndividualEntryIsMalformed() {
        var document = Jsoup.parse("""
                <ul class="news__lists"><li class="news__item">
                    <a class="news__itemLink" href="/information/no-title/"></a>
                </li></ul>
                """, WsInformationPageClient.INFORMATION_URL);
        assertThat(parser.parse(document)).isEmpty();
    }

    private List<NewsItem> fixtureItems() throws IOException {
        try (InputStream fixture = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/ws-information.html"))) {
            return parser.parse(Jsoup.parse(fixture, "UTF-8", WsInformationPageClient.INFORMATION_URL));
        }
    }
}

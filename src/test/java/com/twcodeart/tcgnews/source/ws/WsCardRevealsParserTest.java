package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.product.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class WsCardRevealsParserTest {

    private final WsCardRevealsParser parser = new WsCardRevealsParser();
    private final Product product = new Product(Game.WEISS_SCHWARZ, "Test product",
            "https://ws-tcg.com/products/test/");

    @Test
    void parsesADatedCardRevealWithoutInferringCardText() throws IOException {
        List<NewsItem> items = parser.parse(fixture("ws-card-reveal.html"), product);

        assertThat(items).hasSize(1);
        NewsItem item = items.get(0);
        assertThat(item.getGame()).isEqualTo(Game.WEISS_SCHWARZ);
        assertThat(item.getCategory()).isEqualTo(NewsCategory.CARD_REVEAL);
        assertThat(item.getProductName()).isEqualTo(product.getProductName());
        assertThat(item.getRevealDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(item.getSourceUrl()).isEqualTo(product.getDetailUrl());
        assertThat(item.getImageUrl()).isEqualTo("https://ws-tcg.com/wordpress/wp-content/uploads/test/card-a.png");
        assertThat(item.getContentHash()).matches("[0-9a-f]{64}");
        assertThat(item.getTitle()).isNull();
    }

    @Test
    void keepsEachSameDayImageAndItsOwnDateWhileSkippingMalformedRecords() throws IOException {
        List<NewsItem> items = parser.parse(fixture("ws-card-reveals-archive.html"), product);

        assertThat(items).hasSize(4);
        assertThat(items).extracting(NewsItem::getRevealDate).containsExactly(
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 29),
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 28));
        assertThat(items).extracting(NewsItem::getImageUrl).containsExactly(
                "https://ws-tcg.com/wordpress/wp-content/uploads/test/card-a.png",
                "https://ws-tcg.com/wordpress/wp-content/uploads/test/card-b.png",
                "https://ws-tcg.com/wordpress/wp-content/uploads/test/card-a.png",
                "https://ws-tcg.com/wordpress/wp-content/uploads/test/card-a.png");
        // Parser emits occurrences; the ingestion service owns duplicate filtering.
        assertThat(items.get(0).getContentHash()).isEqualTo(items.get(2).getContentHash());
        assertThat(items.get(0).getContentHash()).isNotEqualTo(items.get(1).getContentHash());
        assertThat(items.get(0).getContentHash()).isNotEqualTo(items.get(3).getContentHash());
    }

    @Test
    void returnsAnEmptyResultWhenThereIsNoDatedCardReveal() throws IOException {
        assertThat(parser.parse(fixture("ws-product-no-reveals.html"), product)).isEmpty();
    }

    private Document fixture(String fileName) throws IOException {
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/" + fileName), "Missing fixture: " + fileName)) {
            return Jsoup.parse(input, "UTF-8", product.getDetailUrl());
        }
    }
}

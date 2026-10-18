package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextNotificationFormatterTest {
    @Test void includesOriginalArticleTextWithoutChangingTheOriginalPostTitle() {
        NewsItem item=new NewsItem(Game.YUGIOH_OCG,NewsCategory.CARD_REVEAL,"Original post",null,null,null,
                "https://yu-gi-oh.jp/media/cms/v1/card.webp","https://x.com/yu_gi_oh_jp/status/101","b".repeat(64));
        item.setArticleUrl("https://yu-gi-oh.jp/news/article/");
        item.setArticleText("新カードの公式説明\n第二行");
        assertThat(formatter.format(item)).contains("Title: Original post", "Article text (original):\n新カードの公式説明\n第二行",
                "https://yu-gi-oh.jp/news/article/");
    }

    private final NotificationFormatter formatter = new PlainTextNotificationFormatter();
    private static final String SOURCE = "https://ws-tcg.com/products/test/";

    @Test
    void formatsCardRevealsWithTheRevealDateAndImageLink() {
        NewsItem item = item(NewsCategory.CARD_REVEAL, "Official reveal notice", "Test product",
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30), "https://ws-tcg.com/cards/test.png");

        assertThat(formatter.format(item)).isEqualTo("""
                [Weiß Schwarz Card Reveal]

                Product: Test product
                Reveal date: 2026-09-29
                Title: Official reveal notice

                Official source:
                https://ws-tcg.com/products/test/

                Image:
                https://ws-tcg.com/cards/test.png""");
    }

    @Test
    void formatsGeneralNewsWithItsOriginalTitleAndPublicationDateWithoutAnImage() {
        NewsItem item = item(NewsCategory.NEWS, "公式のお知らせ", null,
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29), null);

        assertThat(formatter.format(item)).isEqualTo("""
                [Weiß Schwarz News]

                Published date: 2026-09-29
                Title: 公式のお知らせ

                Official source:
                https://ws-tcg.com/products/test/""");
    }

    @Test
    void omitsAllMissingOptionalFieldsWithoutInferringADate() {
        NewsItem item = item(NewsCategory.CARD_REVEAL, null, null,
                null, LocalDate.of(2026, 9, 30), null);

        assertThat(formatter.format(item)).isEqualTo("""
                [Weiß Schwarz Card Reveal]

                Official source:
                https://ws-tcg.com/products/test/""").doesNotContain("null");
    }

    @Test
    void treatsEmptyOrWhitespaceFieldsAsAbsentForCardListNews() {
        NewsItem item = item(NewsCategory.CARD_LIST_NEWS, "", " \t ", null, null, " \n ");

        assertThat(formatter.format(item)).isEqualTo("""
                [Weiß Schwarz Card List News]

                Official source:
                https://ws-tcg.com/products/test/""");
    }

    @Test
    void formatsProductNewsWithAnImageAndNoDate() {
        NewsItem item = item(NewsCategory.PRODUCT_NEWS, "商品情報", "Test product",
                null, null, "https://ws-tcg.com/banner.png");

        assertThat(formatter.format(item)).isEqualTo("""
                [Weiß Schwarz Product News]

                Product: Test product
                Title: 商品情報

                Official source:
                https://ws-tcg.com/products/test/

                Image:
                https://ws-tcg.com/banner.png""");
    }

    private NewsItem item(NewsCategory category, String title, String productName,
                          LocalDate revealDate, LocalDate publishedDate, String imageUrl) {
        return new NewsItem(Game.WEISS_SCHWARZ, category, title, productName,
                revealDate, publishedDate, imageUrl, SOURCE, "a".repeat(64));
    }
}

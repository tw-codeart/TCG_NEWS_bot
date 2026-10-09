package com.twcodeart.tcgnews.source.ocg;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class OcgRssHubFeedParserTest {
    @Test
    void acceptsTheSuppliedAnnouncementHeadingPreservesUnicodeAndFiltersUnwantedOrMalformedEntries() throws Exception {
        byte[] xml;
        try (var input = getClass().getResourceAsStream("/fixtures/ocg-new-cards.xml")) { xml = input.readAllBytes(); }
        var items = new OcgRssHubFeedParser().parse(xml);
        assertThat(items).hasSize(3);
        assertThat(items).allMatch(item -> item.getGame() == Game.YUGIOH_OCG && item.getCategory() == NewsCategory.CARD_REVEAL);
        assertThat(items.get(0).getTitle()).contains("𝕴𝕸𝕸𝕺𝕽𝕿𝕬𝕷", "収録カード公開");
        assertThat(items.get(0).getPublishedAt()).isEqualTo(Instant.parse("2026-10-08T02:00:00Z"));
        assertThat(items.get(0).getPublishedDate()).isEqualTo(LocalDate.of(2026,10,8));
        assertThat(items.get(0).getSourceUrl()).isEqualTo("https://x.com/YuGiOh_OCG_INFO/status/1001");
        assertThat(items.get(0).getProductName()).isNull();
        assertThat(items.get(0).getRevealDate()).isNull();
        assertThat(items).extracting(item -> item.getContentHash()).doesNotHaveDuplicates();
        assertThat(items.get(2).getPublishedAt()).isNull();
    }

    @Test
    void skipsOrdinaryIntroductionsExplicitReprintsAndAnnouncementsWithoutImages() throws Exception {
        var filter = new OcgNewCardPostFilter();
        for (String text : new String[]{"カード紹介", "今日のカード", "新商品予約受付", "収録カード公開 再収録", "収録カード公開 新規イラスト"}) {
            assertThat(filter.test(text)).isFalse();
        }
        assertThat(filter.test("𖤐 収録カード公開 𖤐 収録‼")).isTrue();
        assertThat(new OcgRssHubFeedParser().parse(feed("収録カード公開", "", 1))).isEmpty();
    }

    public static byte[] feed(String title, String images, int id) {
        return ("<rss version=\"2.0\"><channel><title>Test</title><link>https://x.com/YuGiOh_OCG_INFO</link><description>Test</description>"
                + "<item><title>" + title + "</title><link>https://x.com/YuGiOh_OCG_INFO/status/" + id
                + "</link><description><![CDATA[" + images + "]]></description></item></channel></rss>").getBytes(StandardCharsets.UTF_8);
    }
}

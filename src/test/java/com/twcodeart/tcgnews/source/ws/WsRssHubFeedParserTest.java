package com.twcodeart.tcgnews.source.ws;

import com.rometools.rome.io.FeedException;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WsRssHubFeedParserTest {
    private final WsRssHubFeedParser parser = new WsRssHubFeedParser();

    static byte[] fixture(String name) throws Exception {
        try (var stream = Objects.requireNonNull(WsRssHubFeedParserTest.class
                .getResourceAsStream("/fixtures/" + name))) {
            return stream.readAllBytes();
        }
    }

    private List<NewsItem> rss() throws Exception {
        return parser.parse(fixture("ws-rsshub.xml"));
    }

    @Test
    void parsesAllThreeImagesInTheSuppliedEscapedHtmlStructureWithoutConfusingTheNewerSingleImagePost() throws Exception {
        var items=parser.parse(fixture("ws-rsshub-three-images.xml"));
        assertThat(items).hasSize(4);
        var three=items.stream().filter(item -> item.getSourceUrl().endsWith("/2108386969822846981")).toList();
        assertThat(three).hasSize(3).extracting(NewsItem::getImageUrl).containsExactly(
                "https://pbs.twimg.com/media/synthetic-a?format=jpg&name=orig",
                "https://pbs.twimg.com/media/synthetic-b?format=jpg&name=orig",
                "https://pbs.twimg.com/media/synthetic-c?format=jpg&name=orig");
    }

    @Test
    void mapsOriginalPostMetadataWithoutGuessingProductOrRevealDate() throws Exception {
        var item = rss().get(0);
        assertThat(item.getGame()).isEqualTo(Game.WEISS_SCHWARZ);
        assertThat(item.getCategory()).isEqualTo(NewsCategory.CARD_REVEAL);
        assertThat(item.getTitle()).isEqualTo("今日のカード テスト公開");
        assertThat(item.getSourceUrl()).isEqualTo("https://x.com/wstcg/status/100");
        assertThat(item.getPublishedAt()).isEqualTo(Instant.parse("2026-10-05T23:30:00Z"));
        assertThat(item.getPublishedDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(item.getImageUrl()).isEqualTo("https://pbs.twimg.com/media/test-one.jpg");
        assertThat(item.getProductName()).isNull();
        assertThat(item.getRevealDate()).isNull();
        assertThat(item.getCreatedAt()).isNull(); // Assigned by JPA, not by parsing.
        assertThat(item.getContentHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void ignoresUnrelatedPostsAndInvalidIndividualEntriesWhileKeepingValidOnes() throws Exception {
        assertThat(rss()).hasSize(6).extracting(NewsItem::getSourceUrl)
                .containsOnly("https://x.com/wstcg/status/100", "https://x.com/wstcg/status/200",
                        "https://x.com/wstcg/status/300", "https://x.com/wstcg/status/400");
    }

    @Test
    void emitsEachImageSeparatelyAndIgnoresQuotedAvatarAndUnexpectedImages() throws Exception {
        var cards = rss().stream().filter(item -> item.getSourceUrl().endsWith("/200")).toList();
        assertThat(cards).hasSize(2).extracting(NewsItem::getImageUrl).containsExactly(
                "https://pbs.twimg.com/media/test-two?format=jpg&name=orig",
                "https://ws-tcg.com/wordpress/wp-content/uploads/test-three.png");
        assertThat(cards).extracting(NewsItem::getContentHash).doesNotHaveDuplicates();
        assertThat(cards).extracting(NewsItem::getPublishedAt).containsOnly(Instant.parse("2026-10-05T23:30:00Z"));
    }

    @Test
    void retainsPostsWithMissingImageOrDateAndFallsBackToOriginalBodyText() throws Exception {
        var items = rss();
        var noImage = items.stream().filter(item -> item.getSourceUrl().endsWith("/300")).findFirst().orElseThrow();
        assertThat(noImage.getImageUrl()).isNull();
        var noDate = items.stream().filter(item -> item.getSourceUrl().endsWith("/400")).findFirst().orElseThrow();
        assertThat(noDate.getTitle()).isEqualTo("今日のカード 日時なし");
        assertThat(noDate.getPublishedAt()).isNull();
        assertThat(noDate.getPublishedDate()).isNull();
        assertThat(noDate.getImageUrl()).isNotBlank();
    }

    @Test
    void parsesAtomContentAndEnclosuresWithoutTreatingUpdatedTimeAsPublication() throws Exception {
        var items = parser.parse(fixture("ws-rsshub-atom.xml"));
        assertThat(items).hasSize(3);
        assertThat(items.subList(0, 2)).extracting(NewsItem::getImageUrl).containsExactly(
                "https://pbs.twimg.com/media/atom-one.png", "https://pbs.twimg.com/media/atom-two.png");
        assertThat(items.get(0).getTitle()).isEqualTo("今日のカード Atom公開");
        assertThat(items.get(0).getPublishedAt()).isEqualTo(Instant.parse("2026-10-05T23:30:00Z"));
        assertThat(items.get(2).getPublishedAt()).isNull();
        assertThat(items.get(2).getPublishedDate()).isNull();
    }

    @Test
    void returnsEmptyForAnEmptyFeedAndRejectsMalformedXml() throws Exception {
        assertThat(parser.parse(fixture("ws-rsshub-empty.xml"))).isEmpty();
        assertThatThrownBy(() -> parser.parse(fixture("ws-rsshub-malformed.xml")))
                .isInstanceOf(FeedException.class);
    }

    @Test
    void rejectsDoctypesWithoutResolvingExternalEntities() {
        String xml = "<!DOCTYPE rss SYSTEM 'https://external.example/feed.dtd'><rss version='2.0'><channel/></rss>";
        assertThatThrownBy(() -> parser.parse(xml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(FeedException.class);
    }

    @Test
    void leavesDuplicateDecisionsToIngestionAndKeepsIdentityStableAcrossMetadataChanges() throws Exception {
        var items = rss();
        assertThat(items.get(0).getContentHash()).isEqualTo(items.get(5).getContentHash());
        assertThat(items.get(0).getPublishedAt()).isNotEqualTo(items.get(5).getPublishedAt());
        String changed = new String(fixture("ws-rsshub.xml"), StandardCharsets.UTF_8)
                .replace("今日のカード テスト公開", "今日のカード 修正された公開文");
        assertThat(parser.parse(changed.getBytes(StandardCharsets.UTF_8)).get(0).getContentHash())
                .isEqualTo(items.get(0).getContentHash());
    }
}

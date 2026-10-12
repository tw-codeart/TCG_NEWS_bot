package com.twcodeart.tcgnews.source.ocg;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class YuGiOhJpParsersTest {
    static final String URL="https://yu-gi-oh.jp/news/synthetic-article/";
    static String html() throws Exception {return fixture("yugioh-jp-new-cards.html");}
    static String fixture(String name) throws Exception {
        try(var stream=YuGiOhJpParsersTest.class.getResourceAsStream("/fixtures/"+name)) {
            return new String(java.util.Objects.requireNonNull(stream).readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    @Test void selectsAllFiveNewCardsAndExcludesReprintsPackagingLogosAndDialogImages() throws Exception {
        var parsed=new YuGiOhJpArticleParser().parse(html(),URL);
        assertThat(parsed.imageUrls()).hasSize(5).allMatch(url->url.startsWith("https://yu-gi-oh.jp/media/cms/v1/new-"));
        assertThat(parsed.publishedAt()).isEqualTo(Instant.parse("2026-10-12T03:00:05.599Z"));
    }
    @Test void supportsMoreThanTenImagesInAnExplicitNewCardBlock() throws Exception {
        var document=org.jsoup.Jsoup.parse(html());var grid=document.selectFirst("[data-news-body] .grid");
        for(int i=6;i<=15;i++)grid.append("<button data-news-image-open data-image-src='/media/cms/v1/new-"+i+".webp'></button>");
        assertThat(new YuGiOhJpArticleParser().parse(document.outerHtml(),URL).imageUrls()).hasSize(15);
    }
    @Test void malformedImageDoesNotBlockOtherCardsAndDateCanBeAbsent() throws Exception {
        String changed=html().replace("datetime=\"2026-10-12T03:00:05.599Z\"", "")
                .replace("https://yu-gi-oh.jp/media/cms/v1/new-1.webp","https://attacker.example/a.png");
        var article=new YuGiOhJpArticleParser().parse(changed,URL);
        assertThat(article.imageUrls()).hasSize(4);assertThat(article.publishedAt()).isNull();
    }
    @Test void rushAndUnmarkedSectionsAreNotGuessedToBeOcgNewCards() throws Exception {
        assertThat(new YuGiOhJpArticleParser().parse(html().replace("tag=ocg","tag=rush"),URL).imageUrls()).isEmpty();
        assertThat(new YuGiOhJpArticleParser().parse(html().replace("<span>新規カード</span>","<span>カード</span>"),URL).imageUrls()).isEmpty();
        assertThatThrownBy(()->new YuGiOhJpArticleParser().parse("<html>unexpected</html>",URL)).hasMessageContaining("article element missing");
    }
    @Test void feedPreservesOfficialPostTextLinksAndDateWhileIgnoringOtherAccountsAndUnrelatedNews() throws Exception {
        var posts=new YuGiOhJpFeedParser().parse(fixture("yugioh-jp-feed.xml").getBytes(StandardCharsets.UTF_8));
        assertThat(posts).hasSize(2);
        assertThat(posts.get(0).title()).contains("新カードと再録カードを公開");
        assertThat(posts.get(0).sourceUrl()).isEqualTo("https://x.com/yu_gi_oh_jp/status/101");
        assertThat(posts.get(0).articleUrls()).containsExactly(URL);
        assertThat(posts.get(0).publishedAt()).isEqualTo(Instant.parse("2026-10-12T03:00:00Z"));
        assertThat(posts.get(1).publishedAt()).isNull();
    }
    @Test void malformedIndividualFeedEntryDoesNotBlockFollowingValidEntry() throws Exception {
        String feed=fixture("yugioh-jp-feed.xml").replace("https://twitter.com/yu_gi_oh_jp/status/101?s=46","https://attacker.example/status/101");
        assertThat(new YuGiOhJpFeedParser().parse(feed.getBytes(StandardCharsets.UTF_8))).extracting(YuGiOhJpFeedParser.Post::sourceUrl)
                .containsExactly("https://x.com/yu_gi_oh_jp/status/103");
        for(String bad:new String[]{"http://yu-gi-oh.jp/news/a/","https://yu-gi-oh.jp.attacker.example/news/a/","https://yu-gi-oh.jp/news/search/../other/path/"})
            assertThatThrownBy(()->YuGiOhJpUrls.article(bad)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->YuGiOhJpUrls.article("https://yu-gi-oh.jp/news/"+"a".repeat(2100)+"/"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void genericFeedTitleUsesOriginalPostBodyAndProductIsTakenOnlyFromExplicitArticleHeading() throws Exception {
        String feed=fixture("yugioh-jp-feed.xml").replace("【更新情報】CARDニュース(OCG) 新カードと再録カードを公開！","Official account");
        assertThat(new YuGiOhJpFeedParser().parse(feed.getBytes(StandardCharsets.UTF_8)).get(0).title()).contains("新カードと再録カードを公開");
        String article=html().replace("OCG synthetic new cards and reprints","『Synthetic Product』に収録される新カードを公開！");
        assertThat(new YuGiOhJpArticleParser().parse(article,URL).productName()).isEqualTo("Synthetic Product");
    }
    @Test void articleFailureDoesNotPreventAnotherPostAndNoPreviewArtworkIsUsed() throws Exception {
        var feed=org.mockito.Mockito.mock(com.twcodeart.tcgnews.source.rsshub.RssHubFeedClient.class);
        var http=org.mockito.Mockito.mock(YuGiOhJpArticleClient.class);
        org.mockito.Mockito.when(feed.fetch()).thenReturn(fixture("yugioh-jp-feed.xml").getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.when(http.fetch(URL)).thenThrow(new java.io.IOException("synthetic 403"));
        org.mockito.Mockito.when(http.fetch("https://yu-gi-oh.jp/news/another-article/")).thenReturn(html());
        var source=new YuGiOhJpRssHubNewsSource(feed,new YuGiOhJpFeedParser(),http,new YuGiOhJpArticleParser());
        var items=source.fetchNews();assertThat(items).hasSize(5);
        assertThat(items).allMatch(item->item.getSourceUrl().endsWith("/103")&&item.getArticleUrl().endsWith("/another-article/"));
        assertThat(items).extracting(com.twcodeart.tcgnews.news.NewsItem::getContentHash).doesNotHaveDuplicates();
        org.mockito.Mockito.when(http.fetch("https://yu-gi-oh.jp/news/another-article/")).thenThrow(new java.io.IOException("synthetic timeout"));
        assertThatThrownBy(source::fetchNews).isInstanceOf(com.twcodeart.tcgnews.source.NewsSourceException.class);
    }
}

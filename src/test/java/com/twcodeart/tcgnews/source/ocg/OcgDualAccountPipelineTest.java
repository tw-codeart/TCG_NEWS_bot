package com.twcodeart.tcgnews.source.ocg;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.*;
import com.twcodeart.tcgnews.notification.qq.*;
import com.twcodeart.tcgnews.source.rsshub.*;
import com.twcodeart.tcgnews.source.ws.*;
import com.twcodeart.tcgnews.storage.GameStorage;
import com.twcodeart.tcgnews.subscription.QqSubscriptionStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"storage.separated-enabled=true","spring.jpa.hibernate.ddl-auto=update",
        "rsshub.enabled=true","rsshub.ocg-enabled=true","rsshub.ocg-jp-enabled=true","rsshub.base-url=http://feed.example",
        "rsshub.polling-enabled=false","qq.enabled=true","qq.test-group-id=42","qq.group-subscriptions-enabled=true",
        "qq.command-secret=synthetic-command-secret"})
class OcgDualAccountPipelineTest {
    static final Path ROOT=directory();
    static Path directory(){try{return Files.createTempDirectory("dual-account-tests-");}catch(Exception e){throw new IllegalStateException(e);}}
    static String url(String file){return "jdbc:h2:file:"+ROOT.resolve(file).toString().replace('\\','/');}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->url("legacy"));p.add("storage.ws-url",()->url("ws"));p.add("storage.ocg-url",()->url("ocg"));
        p.add("storage.backup-directory",()->ROOT.resolve("backups").toString());
    }
    @Autowired NewsFetchService fetch;
    @Autowired GameStorage storage;
    @Autowired QqSubscriptionStore groups;
    @Autowired NewsQueryService query;
    @MockitoBean QqNotificationSender qq;
    @MockitoSpyBean OcgRssHubNewsSource info;
    @MockitoSpyBean YuGiOhJpRssHubNewsSource jp;
    @MockitoSpyBean WsRssHubNewsSource ws;
    private NotificationSender group;
    private RssHubFeedClient feed;
    private YuGiOhJpArticleClient articles;
    @BeforeEach void reset() throws Exception {
        for(var game:Game.values())storage.execute(game,r -> {
            r.groupDeliveries().deleteAll();r.subscriptions().deleteAll();r.commands().deleteAll();r.deliveries().deleteAll();
            r.cursors().deleteAll();r.bootstraps().deleteAll();r.news().deleteAll();return null;
        });
        group=mock(NotificationSender.class);when(group.acceptsImages()).thenReturn(true);
        when(qq.acceptsImages()).thenReturn(true);when(qq.forSubscribedGroup(42)).thenReturn(group);
        feed=mock(RssHubFeedClient.class);articles=mock(YuGiOhJpArticleClient.class);
        var actual=new YuGiOhJpRssHubNewsSource(feed,new YuGiOhJpFeedParser(),articles,new YuGiOhJpArticleParser());
        doAnswer(call->actual.fetchNews()).when(jp).fetchNews();
        doReturn(List.of(card(Game.YUGIOH_OCG,1))).when(info).fetchNews();
        doReturn(List.of(card(Game.WEISS_SCHWARZ,1))).when(ws).fetchNews();
        when(feed.fetch()).thenReturn(feed(100,Instant.EPOCH));article(100);
    }
    NewsItem card(Game game,long id){
        String source=(game==Game.WEISS_SCHWARZ?"https://x.com/wstcg/status/":"https://x.com/YuGiOh_OCG_INFO/status/")+id;
        String image="https://pbs.twimg.com/media/"+game+"-"+id+".png";
        return new NewsItem(game,NewsCategory.CARD_REVEAL,"Synthetic "+game,null,null,null,Instant.EPOCH,image,source,PostImageContentHash.generate(game,source,image));
    }
    byte[] feed(long id,Instant published){
        String date=published==null?"":"<pubDate>"+DateTimeFormatter.RFC_1123_DATE_TIME.format(published.atZone(ZoneOffset.UTC))+"</pubDate>";
        return ("<rss version='2.0'><channel><title>Fixture</title><link>https://x.com/yu_gi_oh_jp</link><description>Fixture</description>"
                +"<item><title>OCG 新カードと再録カードを公開！</title><link>https://x.com/yu_gi_oh_jp/status/"+id+"</link>"+date
                +"<description><![CDATA[<a href='https://yu-gi-oh.jp/news/article-"+id+"/'>公式記事</a>]]></description></item></channel></rss>").getBytes(StandardCharsets.UTF_8);
    }
    void article(long id) throws Exception {
        when(articles.fetch("https://yu-gi-oh.jp/news/article-"+id+"/")).thenReturn(YuGiOhJpParsersTest.html().replace("new-","card-"+id+"-"));
    }
    NewsFetchSummary run(){return fetch.fetchSourcesByIds(List.of(WsRssHubNewsSource.SOURCE_ID,OcgRssHubNewsSource.SOURCE_ID,YuGiOhJpRssHubNewsSource.SOURCE_ID));}

    @Test void enablingJpHistoryDoesNotFloodAnAlreadyActiveOcgSubscription() throws Exception {
        when(feed.fetch()).thenReturn("<rss version='2.0'><channel><title>Empty</title><link>https://x.com/yu_gi_oh_jp</link><description>Fixture</description></channel></rss>".getBytes(StandardCharsets.UTF_8));
        run();verify(group,never()).sendBatch(anyList());
        when(feed.fetch()).thenReturn(feed(100,Instant.EPOCH));
        doReturn(List.of(card(Game.YUGIOH_OCG,1),card(Game.YUGIOH_OCG,2))).when(info).fetchNews();
        var result=run();assertThat(result.saved()).isEqualTo(6);
        verify(group).sendBatch(argThat(messages->messages.size()==1&&messages.get(0).imageUrl().contains("YUGIOH_OCG-2")));
        assertThat(query.findNews(Game.YUGIOH_OCG)).hasSize(7);
    }
    @Test void bothOcgAccountsMergeIntoOnePacketAndDuplicateFetchSendsNothing() throws Exception {
        run();verify(group,never()).sendBatch(anyList());
        when(feed.fetch()).thenReturn(feed(101,Instant.now().plusSeconds(30)));article(101);
        doReturn(List.of(card(Game.YUGIOH_OCG,1),card(Game.YUGIOH_OCG,2))).when(info).fetchNews();
        var first=run();assertThat(first.saved()).isEqualTo(6);
        verify(group,times(1)).sendBatch(argThat(messages->messages.size()==6));
        var second=run();assertThat(second.saved()).isZero();verify(group,times(1)).sendBatch(anyList());
        var preview=query.findLatestXPosts(Game.YUGIOH_OCG,1);assertThat(preview.get(0)).hasSize(5);
        assertThat(query.findXPost(Game.YUGIOH_OCG,"101").get(0)).allMatch(item->item.getArticleUrl().endsWith("/article-101/"));
        assertThat(groups.status(Game.YUGIOH_OCG).sentItems()).isEqualTo(6);
    }
    @Test void lateHistoricalOrUndatedJpArticlesAreStoredWithoutAutomaticDelivery() throws Exception {
        run();when(feed.fetch()).thenReturn(feed(99,Instant.EPOCH));article(99);
        assertThat(run().saved()).isEqualTo(5);verify(group,never()).sendBatch(anyList());
        when(feed.fetch()).thenReturn(feed(98,null));article(98);
        assertThat(run().saved()).isEqualTo(5);verify(group,never()).sendBatch(anyList());
    }
    @Test void failedQqDeliveryPreservesArticleImagesAndRetriesOnlyPendingRecords() throws Exception {
        run();when(feed.fetch()).thenReturn(feed(101,Instant.now().plusSeconds(30)));article(101);
        doThrow(new QqNotificationException(QqNotificationException.Reason.UNAVAILABLE,"Synthetic failure")).when(group).sendBatch(anyList());
        assertThat(run().saved()).isEqualTo(5);assertThat(groups.status(Game.YUGIOH_OCG).pendingItems()).isEqualTo(5);
        assertThat(query.findXPost(Game.YUGIOH_OCG,"101").get(0)).hasSize(5);
        doNothing().when(group).sendBatch(anyList());assertThat(run().saved()).isZero();
        assertThat(groups.status(Game.YUGIOH_OCG).pendingItems()).isZero();assertThat(groups.status(Game.YUGIOH_OCG).sentItems()).isEqualTo(5);
        run();verify(group,times(2)).sendBatch(anyList());
    }
    @Test void articleFailureDoesNotBlockWsOrOriginalOcgAccount() throws Exception {
        run();when(articles.fetch(anyString())).thenThrow(new java.io.IOException("Synthetic 403"));
        doReturn(List.of(card(Game.WEISS_SCHWARZ,1),card(Game.WEISS_SCHWARZ,2))).when(ws).fetchNews();
        doReturn(List.of(card(Game.YUGIOH_OCG,1),card(Game.YUGIOH_OCG,2))).when(info).fetchNews();
        var result=run();assertThat(result.errors()).extracting(NewsFetchSummary.SourceError::sourceId).containsExactly(YuGiOhJpRssHubNewsSource.SOURCE_ID);
        assertThat(result.saved()).isEqualTo(2);verify(group,times(2)).sendBatch(argThat(messages->messages.size()==1));
    }
}

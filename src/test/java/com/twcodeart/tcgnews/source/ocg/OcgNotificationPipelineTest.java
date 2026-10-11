package com.twcodeart.tcgnews.source.ocg;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.*;
import com.twcodeart.tcgnews.notification.qq.QqNotificationSender;
import com.twcodeart.tcgnews.notification.qq.QqNotificationException;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.rsshub.RssHubFeedClient;
import com.twcodeart.tcgnews.source.ws.RssHubProperties;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"rsshub.enabled=true","rsshub.ocg-enabled=true","rsshub.base-url=https://rsshub.example","qq.enabled=true"})
@Import({NewsIngestionService.class, NewsDeduplicationPolicy.class, NewsNotificationService.class,
        PlainTextNotificationFormatter.class, ConsoleNotificationSender.class, RssHubProperties.class,
        RssHubQqNotificationService.class, QqDeliveryStore.class, com.twcodeart.tcgnews.storage.GameStorage.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OcgNotificationPipelineTest {
    @Autowired private NewsItemRepository news;
    @Autowired private SourceNotificationBootstrapRepository bootstraps;
    @Autowired private QqDeliveryCursorRepository cursors;
    @Autowired private QqNewsDeliveryRepository deliveries;
    @Autowired private NewsIngestionService ingestion;
    @Autowired private NewsNotificationService notifications;
    @Autowired private ObjectProvider<RssHubQqNotificationService> rss;
    @Autowired private RssHubProperties properties;
    @MockitoBean private QqNotificationSender qq;
    private RssHubFeedClient client;
    private OcgRssHubNewsSource source;

    @BeforeEach void reset() {
        deliveries.deleteAll(); cursors.deleteAll();
        news.deleteAll(); bootstraps.deleteAll(); properties.setNotificationBatchLimit(3);
        when(qq.acceptsImages()).thenReturn(true);
        properties.setOcgEnabled(true);
        client=mock(RssHubFeedClient.class); source=new OcgRssHubNewsSource(client,new OcgRssHubFeedParser());
    }
    @Test void bootstrapsThenForwardsAllThirteenNewImagesInOnePacketAndNeverResendsDuplicates() throws Exception {
        bootstrap();
        when(client.fetch()).thenReturn(feed(200,13));
        assertThat(fetch(source).saved()).isEqualTo(13);
        var packet=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(qq).sendBatch(packet.capture());
        @SuppressWarnings("unchecked") List<NotificationMessage> messages=packet.getValue();
        assertThat(messages).hasSize(13).extracting(NotificationMessage::imageUrl).doesNotHaveDuplicates();
        assertThat(messages).allMatch(message -> message.text().contains("Yu-Gi-Oh! OCG"));
        assertThat(fetch(source).duplicates()).isEqualTo(13);
        verify(qq,times(1)).sendBatch(anyList());
        assertThat(news.count()).isEqualTo(14);
    }
    @Test void combinesFortyFiveImagesInOnePacketWithoutDroppingImages() throws Exception {
        bootstrap(); when(client.fetch()).thenReturn(feed(200,45));
        assertThat(fetch(source).saved()).isEqualTo(45);
        var packets=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(qq,times(1)).sendBatch(packets.capture());
        assertThat(packets.getAllValues()).extracting(List::size).containsExactly(45);
    }
    @Test void failedForwardLeavesNewRecordsCommittedAndRetriesAfterRecovery() throws Exception {
        bootstrap(); when(client.fetch()).thenReturn(feed(200,13));
        doThrow(new QqNotificationException(QqNotificationException.Reason.UNAVAILABLE,"QQ API unavailable"))
                .when(qq).sendBatch(anyList());
        assertThat(fetch(source).saved()).isEqualTo(13);
        assertThat(news.count()).isEqualTo(14);
        doNothing().when(qq).sendBatch(anyList());
        assertThat(fetch(source).saved()).isZero();
        verify(qq,times(2)).sendBatch(anyList());
    }
    @Test void ocgFailureDoesNotPreventAnUnrelatedWebsiteSourceFromSaving() throws Exception {
        when(client.fetch()).thenThrow(new IOException("Feed unavailable"));
        var website=mock(NewsSource.class);
        when(website.getSourceId()).thenReturn("test-website");
        when(website.fetchNews()).thenReturn(List.of(new NewsItem(Game.WEISS_SCHWARZ,NewsCategory.NEWS,
                "Website notice",null,null,null,"https://ws-tcg.com/information/test/","f".repeat(64))));
        var result=fetch(source,website);
        assertThat(result.saved()).isEqualTo(1); assertThat(result.errors()).hasSize(1);
        assertThat(news.count()).isEqualTo(1); assertThat(bootstraps.count()).isZero();
        verifyNoInteractions(qq);
    }
    @Test void anExistingWsBaselineDoesNotDisableOcgFirstRunHistorySuppression() throws Exception {
        bootstraps.saveAndFlush(new SourceNotificationBootstrap(WsRssHubNewsSource.SOURCE_ID));
        when(client.fetch()).thenReturn(feed(100,13));
        assertThat(fetch(source).saved()).isEqualTo(13);
        assertThat(bootstraps.existsById(OcgRssHubNewsSource.SOURCE_ID)).isTrue();
        assertThat(bootstraps.existsById(WsRssHubNewsSource.SOURCE_ID)).isTrue();
        verifyNoInteractions(qq);
    }
    @Test void wsAndOcgCanUseTheSameImageWithoutMergingTheirGameIdentity() throws Exception {
        bootstrap();
        bootstraps.saveAndFlush(new SourceNotificationBootstrap(WsRssHubNewsSource.SOURCE_ID));
        when(client.fetch()).thenReturn(feed(200,13));
        var ws = wsSource(13);
        assertThat(fetch(ws,source).saved()).isEqualTo(26);
        assertThat(news.count()).isEqualTo(27);
        verify(qq,times(2)).sendBatch(anyList());
    }
    @Test void sharesTheThreePacketBudgetAcrossSourcesAndStoresExcessWithoutFlooding() throws Exception {
        bootstrap();
        bootstraps.saveAndFlush(new SourceNotificationBootstrap(WsRssHubNewsSource.SOURCE_ID));
        when(client.fetch()).thenReturn(feed(200,13));
        var ws=wsSource(101);
        assertThat(fetch(ws,source).saved()).isEqualTo(114);
        verify(qq,times(3)).sendBatch(anyList());
        assertThat(news.count()).isEqualTo(115);
        assertThat(fetch(ws,source).saved()).isZero();
        verify(qq,times(4)).sendBatch(anyList());
    }
    private NewsSource wsSource(int count) throws Exception {
        var ws=mock(NewsSource.class);
        when(ws.getSourceId()).thenReturn(WsRssHubNewsSource.SOURCE_ID);
        when(ws.fetchNews()).thenReturn(IntStream.range(0,count).mapToObj(n -> new NewsItem(
                Game.WEISS_SCHWARZ,NewsCategory.CARD_REVEAL,"今日のカード",null,null,
                "https://pbs.twimg.com/media/synthetic-200-"+n+".png","https://x.com/wstcg/status/200",
                com.twcodeart.tcgnews.source.rsshub.PostImageContentHash.generate(Game.WEISS_SCHWARZ,
                        "https://x.com/wstcg/status/200","https://pbs.twimg.com/media/synthetic-200-"+n+".png"))).toList());
        return ws;
    }
    private void bootstrap() throws Exception {
        when(client.fetch()).thenReturn(feed(100,1)); assertThat(fetch(source).saved()).isEqualTo(1); verifyNoInteractions(qq);
    }
    private byte[] feed(int id,int count) {
        String images=IntStream.range(0,count).mapToObj(n -> "<img src=\"https://pbs.twimg.com/media/synthetic-"+id+"-"+n+".png\"/>")
                .collect(java.util.stream.Collectors.joining());
        return OcgRssHubFeedParserTest.feed("𖤐 収録カード公開 𖤐",images,id);
    }
    private NewsFetchSummary fetch(NewsSource... sources) {
        return new NewsFetchService(List.of(sources),ingestion,notifications,rss).fetchAll();
    }
}

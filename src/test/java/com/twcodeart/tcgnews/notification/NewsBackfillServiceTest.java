package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.qq.*;
import com.twcodeart.tcgnews.source.*;
import com.twcodeart.tcgnews.source.ws.*;
import com.twcodeart.tcgnews.source.ocg.*;
import com.twcodeart.tcgnews.storage.GameStorage;
import com.twcodeart.tcgnews.subscription.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"rsshub.enabled=true", "qq.enabled=true", "qq.test-group-id=42"})
@Import({NewsIngestionService.class, NewsDeduplicationPolicy.class, NewsNotificationService.class,
        PlainTextNotificationFormatter.class, ConsoleNotificationSender.class, RssHubProperties.class,
        RssHubQqNotificationService.class, QqDeliveryStore.class, GameStorage.class, QqProperties.class, QqSubscriptionStore.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class NewsBackfillServiceTest {
    @Autowired NewsItemRepository news;
    @Autowired QqDeliveryStore deliveryStore;
    @Autowired SourceNotificationBootstrapRepository baselines;
    @Autowired QqDeliveryCursorRepository cursors;
    @Autowired QqNewsDeliveryRepository deliveries;
    @Autowired NewsIngestionService ingestion;
    @Autowired NewsNotificationService notifications;
    @Autowired ObjectProvider<RssHubQqNotificationService> rss;
    @Autowired QqProperties qqProperties;
    @Autowired GameStorage storage;
    @Autowired QqSubscriptionStore subscriptions;
    @Autowired QqGroupDeliveryRepository groupDeliveries;
    @Autowired QqGroupSubscriptionRepository groups;
    @Autowired ProcessedQqCommandRepository commands;
    @MockitoBean QqNotificationSender qq;
    private NewsSource ws, ocg, articles;
    private NewsFetchService fetch;
    private NewsBackfillService service;
    private TestClock clock;

    @BeforeEach
    void setUp() throws Exception {
        groupDeliveries.deleteAll(); groups.deleteAll(); commands.deleteAll();
        deliveries.deleteAll(); cursors.deleteAll(); baselines.deleteAll(); news.deleteAll();
        qqProperties.setEnabled(true); qqProperties.setTestGroupId("42"); qqProperties.setGroupSubscriptionsEnabled(false);
        clock=new TestClock(Instant.parse("2026-10-18T10:00:00Z"));
        ws=source(WsRssHubNewsSource.SOURCE_ID,Game.WEISS_SCHWARZ);
        ocg=source(OcgRssHubNewsSource.SOURCE_ID,Game.YUGIOH_OCG);
        articles=source(YuGiOhJpRssHubNewsSource.SOURCE_ID,Game.YUGIOH_OCG);
        when(qq.acceptsImages()).thenReturn(true);
        fetch=new NewsFetchService(List.of(ws,ocg,articles),ingestion,notifications,rss);
        service=new NewsBackfillService(fetch,storage,qqProperties,clock);
    }

    @Test
    void recoversDeletedDataWithPreviewThenQueuesAllThreeImagesWithoutSendingHistory() throws Exception {
        when(ws.fetchNews()).thenReturn(history());
        var preview=service.preview(Game.WEISS_SCHWARZ,2,null,null);
        assertThat(preview.records()).isEqualTo(3);
        assertThat(preview.posts()).isEqualTo(1);
        assertThat(preview.entries().get(0).records()).isEqualTo(3);
        assertThat(preview.toQueue()).isEqualTo(3);
        assertThat(news.count()).isEqualTo(5);
        assertThat(deliveries.count()).isZero();
        assertThat(baselines.existsById(WsRssHubNewsSource.SOURCE_ID)).isTrue();
        verifyNoInteractions(qq);
        assertThat(service.confirm(preview.previewId()).queued()).isEqualTo(3);
        verifyNoInteractions(qq);
        fetch.fetchAll();
        assertThat(deliveryStore.status(WsRssHubNewsSource.SOURCE_ID).sentItems()).isEqualTo(3);
        assertThat(deliveryStore.status(WsRssHubNewsSource.SOURCE_ID).pendingItems()).isZero();
        verify(qq).sendBatch(argThat(messages -> messages.size()==3 && messages.stream().allMatch(message -> message.imageUrl()!=null)));
    }

    @Test
    void repeatedPreviewAndConfirmationNeverResetSentOrPendingStamps() throws Exception {
        when(ws.fetchNews()).thenReturn(history());
        var first=service.preview(Game.WEISS_SCHWARZ,2,null,null);
        assertThat(service.confirm(first.previewId()).queued()).isEqualTo(3);
        var repeated=service.confirm(first.previewId());
        assertThat(repeated.queued()).isZero(); assertThat(repeated.alreadyPending()).isEqualTo(3);
        fetch.fetchAll();
        var again=service.preview(Game.WEISS_SCHWARZ,2,null,null);
        assertThat(again.alreadySent()).isEqualTo(3); assertThat(again.toQueue()).isZero();
        assertThat(service.confirm(again.previewId()).queued()).isZero();
        fetch.fetchAll();
        verify(qq,times(1)).sendBatch(anyList());
    }

    @Test
    void failedDeliveryRetainsRowsAndRecoversEvenWhenTheNextFeedFetchFails() throws Exception {
        when(ws.fetchNews()).thenReturn(history());
        var preview=service.preview(Game.WEISS_SCHWARZ,2,null,null);
        service.confirm(preview.previewId());
        doThrow(new QqNotificationException(QqNotificationException.Reason.UNAVAILABLE,"synthetic failure")).when(qq).sendBatch(anyList());
        fetch.fetchAll();
        assertThat(news.count()).isEqualTo(5);
        assertThat(deliveryStore.status(WsRssHubNewsSource.SOURCE_ID).pendingItems()).isEqualTo(3);
        doNothing().when(qq).sendBatch(anyList());
        when(ws.fetchNews()).thenThrow(new NewsSourceException("synthetic RSS failure",null));
        fetch.fetchAll();
        assertThat(deliveryStore.status(WsRssHubNewsSource.SOURCE_ID).sentItems()).isEqualTo(3);
    }

    @Test
    void filtersByGameAndDateAndReportsPartialSourceFailure() throws Exception {
        when(ws.fetchNews()).thenReturn(history());
        when(ocg.fetchNews()).thenReturn(List.of(item(Game.YUGIOH_OCG,"200","ocg",clock.instant().minus(Duration.ofDays(1)))));
        when(articles.fetchNews()).thenThrow(new NewsSourceException("synthetic article failure",null));
        var preview=service.preview(Game.YUGIOH_OCG,2,null,null);
        assertThat(preview.records()).isEqualTo(1);
        assertThat(preview.sources()).hasSize(2).anyMatch(report -> report.errorId()!=null);
        assertThat(service.confirm(preview.previewId()).queued()).isEqualTo(1);
        assertThat(deliveries.findAll()).hasSize(1).allMatch(value -> news.findById(value.getNewsId()).orElseThrow().getGame()==Game.YUGIOH_OCG);
        verify(ws,never()).fetchNews();
    }

    @Test
    void defaultsToTheSevenPreviousCompleteJapanDatesAndExcludesToday() throws Exception {
        when(ws.fetchNews()).thenReturn(List.of(
                item(Game.WEISS_SCHWARZ,"1","before",Instant.parse("2026-10-10T14:59:59Z")),
                item(Game.WEISS_SCHWARZ,"2","first-midnight",Instant.parse("2026-10-10T15:00:00Z")),
                item(Game.WEISS_SCHWARZ,"3","last-second",Instant.parse("2026-10-17T14:59:59Z")),
                item(Game.WEISS_SCHWARZ,"4","today-midnight",Instant.parse("2026-10-17T15:00:00Z")),
                item(Game.WEISS_SCHWARZ,"5","today",clock.instant().minusSeconds(3600))));
        var preview=service.preview(Game.WEISS_SCHWARZ,null,null,null);
        assertThat(preview.from()).isEqualTo(Instant.parse("2026-10-10T15:00:00Z"));
        assertThat(preview.until()).isEqualTo(Instant.parse("2026-10-17T15:00:00Z"));
        assertThat(preview.records()).isEqualTo(2);
        assertThat(preview.entries()).extracting(NewsBackfillService.Post::sourceUrl)
                .containsExactly("https://x.com/wstcg/status/2","https://x.com/wstcg/status/3");
    }

    @Test
    void usesJapanCalendarDatesAndAnExclusiveUpperBoundary() throws Exception {
        when(ws.fetchNews()).thenReturn(List.of(
                item(Game.WEISS_SCHWARZ,"1","before",Instant.parse("2026-10-15T14:59:59Z")),
                item(Game.WEISS_SCHWARZ,"2","start",Instant.parse("2026-10-15T15:00:00Z")),
                item(Game.WEISS_SCHWARZ,"3","end",Instant.parse("2026-10-17T15:00:00Z"))));
        var preview=service.preview(Game.WEISS_SCHWARZ,null,LocalDate.parse("2026-10-16"),LocalDate.parse("2026-10-17"));
        assertThat(preview.records()).isEqualTo(1);
        assertThat(preview.entries().get(0).sourceUrl()).endsWith("/2");
    }

    @Test
    void rejectsInvalidWindowsExpiredPreviewsAndChangedTargets() throws Exception {
        assertThatThrownBy(() -> service.preview(Game.WEISS_SCHWARZ,8,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview(Game.WEISS_SCHWARZ,0,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview(Game.WEISS_SCHWARZ,null,LocalDate.now(clock),null)).isInstanceOf(IllegalArgumentException.class);
        when(ws.fetchNews()).thenReturn(history());
        var preview=service.preview(Game.WEISS_SCHWARZ,2,null,null);
        qqProperties.setTestGroupId("43");
        assertThatThrownBy(() -> service.confirm(preview.previewId())).isInstanceOf(IllegalArgumentException.class);
        qqProperties.setTestGroupId("42"); qqProperties.setEnabled(false);
        assertThatThrownBy(() -> service.confirm(preview.previewId())).isInstanceOf(QqNotificationException.class);
        qqProperties.setEnabled(true); clock.value=clock.value.plusSeconds(601);
        assertThatThrownBy(() -> service.confirm(preview.previewId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(deliveries.count()).isZero();
    }

    @Test
    void groupModeBackfillsOnlyTheConfiguredSubscribedTestGroup() throws Exception {
        qqProperties.setGroupSubscriptionsEnabled(true);
        subscriptions.change(Game.WEISS_SCHWARZ,99,true,"other-group");
        ObjectProvider<QqGroupDeliveryService> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new QqGroupDeliveryService(subscriptions,deliveryStore,qqProperties,qq,notifications));
        fetch=new NewsFetchService(List.of(ws),ingestion,notifications,rss,provider);
        service=new NewsBackfillService(fetch,storage,qqProperties,clock);
        when(ws.fetchNews()).thenReturn(history());
        var preview=service.preview(Game.WEISS_SCHWARZ,2,null,null);
        service.confirm(preview.previewId());
        assertThat(groupDeliveries.countByIdGameAndIdGroupIdAndPending(Game.WEISS_SCHWARZ,42,true)).isEqualTo(3);
        assertThat(groupDeliveries.countByIdGameAndIdGroupIdAndPending(Game.WEISS_SCHWARZ,99,true)).isZero();
        assertThat(deliveries.count()).isZero();
        subscriptions.change(Game.WEISS_SCHWARZ,42,false,"cancel-target");
        assertThatThrownBy(() -> service.confirm(preview.previewId())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyOrFailedFirstFeedDoesNotBecomeAFalseHistoryBaseline() throws Exception {
        assertThat(service.preview(Game.WEISS_SCHWARZ,2,null,null).records()).isZero();
        assertThat(baselines.count()).isZero();
        when(ws.fetchNews()).thenThrow(new NewsSourceException("synthetic unavailable",null));
        var failed=service.preview(Game.WEISS_SCHWARZ,2,null,null);
        assertThat(failed.sources().get(0).errorId()).isNotBlank();
        assertThat(baselines.count()).isZero(); assertThat(deliveries.count()).isZero();
    }

    private NewsSource source(String id,Game game) throws Exception {
        var source=mock(NewsSource.class);
        when(source.getSourceId()).thenReturn(id); when(source.getGame()).thenReturn(game); when(source.fetchNews()).thenReturn(List.of());
        return source;
    }
    private List<NewsItem> history() {
        return List.of(item(Game.WEISS_SCHWARZ,"100","a",clock.instant().minus(Duration.ofDays(1))),
                item(Game.WEISS_SCHWARZ,"100","b",clock.instant().minus(Duration.ofDays(1))),
                item(Game.WEISS_SCHWARZ,"100","c",clock.instant().minus(Duration.ofDays(1))),
                item(Game.WEISS_SCHWARZ,"90","old",clock.instant().minus(Duration.ofDays(5))),
                item(Game.WEISS_SCHWARZ,"80","undated",null));
    }
    private NewsItem item(Game game,String post,String image,Instant time) {
        String url="https://x.com/"+(game==Game.WEISS_SCHWARZ?"wstcg":"YuGiOh_OCG_INFO")+"/status/"+post;
        return new NewsItem(game,NewsCategory.CARD_REVEAL,"Original card announcement",null,null,
                time==null?null:time.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate(),time,
                "https://pbs.twimg.com/media/"+image+".png",url,
                com.twcodeart.tcgnews.source.rsshub.PostImageContentHash.generate(game,url,"https://pbs.twimg.com/media/"+image+".png"));
    }
    private static class TestClock extends Clock {
        Instant value;
        TestClock(Instant value){this.value=value;}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(value,zone);}
        public Instant instant(){return value;}
    }
}

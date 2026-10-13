package com.twcodeart.tcgnews.subscription;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.*;
import com.twcodeart.tcgnews.notification.qq.*;
import com.twcodeart.tcgnews.source.ws.*;
import com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource;
import com.twcodeart.tcgnews.storage.GameStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"storage.separated-enabled=true","spring.jpa.hibernate.ddl-auto=update",
        "rsshub.enabled=true","rsshub.ocg-enabled=true","rsshub.base-url=http://feed.example","rsshub.polling-enabled=false",
        "qq.enabled=true","qq.adapter=llbot","qq.ocr-provider=onebot","qq.test-group-id=42",
        "qq.group-subscriptions-enabled=true","qq.command-secret=synthetic-command-secret"})
@AutoConfigureMockMvc
class QqGroupSubscriptionIntegrationTest {
    static final Path DIRECTORY=directory();
    @DynamicPropertySource static void settings(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->url("legacy"));p.add("storage.ws-url",()->url("ws"));p.add("storage.ocg-url",()->url("ocg"));
        p.add("storage.backup-directory",()->DIRECTORY.resolve("backups").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired GameStorage storage;
    @Autowired QqSubscriptionStore store;
    @Autowired NewsFetchService fetch;
    @Autowired QqDeliveryStore legacy;
    @MockitoBean QqNotificationSender qq;
    @MockitoBean WsRssHubFeedClient wsClient;
    @MockitoSpyBean OcgRssHubNewsSource ocg;
    private NotificationSender defaultGroup,secondGroup;
    private int eventId;
    @BeforeEach void reset() throws Exception {
        for(var game:Game.values())storage.execute(game,repos -> {
            repos.groupDeliveries().deleteAll();repos.subscriptions().deleteAll();repos.commands().deleteAll();
            repos.deliveries().deleteAll();repos.cursors().deleteAll();repos.bootstraps().deleteAll();repos.news().deleteAll();return null;
        });
        defaultGroup=mock(NotificationSender.class);secondGroup=mock(NotificationSender.class);
        when(defaultGroup.acceptsImages()).thenReturn(true);when(secondGroup.acceptsImages()).thenReturn(true);
        when(qq.acceptsImages()).thenReturn(true);when(qq.botUserId()).thenReturn(123L);
        when(qq.memberRole(anyLong(),anyLong())).thenReturn("admin");
        when(qq.forSubscribedGroup(42)).thenReturn(defaultGroup);when(qq.forSubscribedGroup(99)).thenReturn(secondGroup);
        when(wsClient.fetch()).thenReturn(feed(100,1));doReturn(List.of()).when(ocg).fetchNews();eventId=0;
    }
    @Test void onlyVerifiedAdminsCanSubscribeAndBodyRoleCannotGrantPermission() throws Exception {
        when(qq.memberRole(99,7)).thenReturn("member");
        event("/news subscribe ws").andExpect(status().isNoContent());
        assertThat(store.active(Game.WEISS_SCHWARZ,99)).isFalse();
        verify(secondGroup).send(contains("只有群主或群管理员"));
        when(qq.memberRole(99,7)).thenReturn("owner");
        event("/news subscribe ws").andExpect(status().isNoContent());
        assertThat(store.active(Game.WEISS_SCHWARZ,99)).isTrue();
        assertThat(store.active(Game.YUGIOH_OCG,99)).isFalse();
    }
    @Test void reportsLlbotSelectionWithoutExposingApiOrCallbackSecrets() throws Exception {
        mvc.perform(get("/api/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.qqAdapter").value("llbot"))
                .andExpect(jsonPath("$.ocrProvider").value("onebot"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.commandSecret").doesNotExist());
    }
    @Test void invalidMissingAndTamperedSignaturesMakeNoQqRequests() throws Exception {
        byte[] body=body("/news subscribe ws");
        mvc.perform(post("/api/qq/events").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/qq/events").contentType("application/json").content(body).header("X-Signature",sign("other".getBytes())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(qq);
        assertThat(store.active(Game.WEISS_SCHWARZ,99)).isFalse();
    }
    @Test void duplicateCallbackChangesPreferenceAndRepliesOnlyOnce() throws Exception {
        byte[] body=body("/news subscribe ws");
        for(int i=0;i<2;i++)mvc.perform(post("/api/qq/events").contentType("application/json").content(body).header("X-Signature",sign(body)))
                .andExpect(status().isNoContent());
        verify(secondGroup,times(1)).send(anyString());
        assertThat(store.groupCount(Game.WEISS_SCHWARZ)).isEqualTo(1);
    }
    @Test void replyFailureDoesNotUndoSavedSubscription() throws Exception {
        doThrow(new QqNotificationException(QqNotificationException.Reason.UNAVAILABLE,"Unavailable"))
                .when(secondGroup).send(anyString());
        event("/news subscribe ws").andExpect(status().isNoContent());
        assertThat(store.active(Game.WEISS_SCHWARZ,99)).isTrue();
    }
    @Test void aDeniedOcgEventCannotBeReplayedAfterTheMemberBecomesAnAdmin() throws Exception {
        byte[] body=body("/news subscribe ocg");
        when(qq.memberRole(99,7)).thenReturn("member");signed(body).andExpect(status().isNoContent());
        when(qq.memberRole(99,7)).thenReturn("admin");signed(body).andExpect(status().isNoContent());
        assertThat(store.active(Game.YUGIOH_OCG,99)).isFalse();verify(secondGroup,times(1)).send(anyString());
    }
    @Test void oneGroupFailureDoesNotReplayTheSuccessfulGroupAndCancellationStopsPendingDelivery() throws Exception {
        baseline();event("/news subscribe ws").andExpect(status().isNoContent());
        when(wsClient.fetch()).thenReturn(combinedFeed());
        doThrow(new QqNotificationException(QqNotificationException.Reason.UNAVAILABLE,"Unavailable"))
                .when(secondGroup).sendBatch(anyList());
        assertThat(fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID).saved()).isEqualTo(3);
        verify(defaultGroup).sendBatch(anyList());verify(secondGroup).sendBatch(anyList());
        assertThat(store.pending(new QqSubscriptionStore.Target(Game.WEISS_SCHWARZ,42))).isEmpty();
        assertThat(store.pending(new QqSubscriptionStore.Target(Game.WEISS_SCHWARZ,99))).hasSize(3);
        doNothing().when(secondGroup).sendBatch(anyList());
        assertThat(fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID).saved()).isZero();
        verify(defaultGroup,times(1)).sendBatch(anyList());verify(secondGroup,times(2)).sendBatch(anyList());
        event("/news unsubscribe ws").andExpect(status().isNoContent());
        when(wsClient.fetch()).thenReturn(feed(300,2));fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);
        verify(secondGroup,times(2)).sendBatch(anyList());
        assertThat(store.active(Game.WEISS_SCHWARZ,99)).isFalse();
    }
    @Test void newSubscriptionAndResubscriptionDoNotSendAlreadyStoredHistory() throws Exception {
        baseline();event("/news subscribe ws").andExpect(status().isNoContent());
        fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);verify(secondGroup,never()).sendBatch(anyList());
        event("/news unsubscribe ws").andExpect(status().isNoContent());
        when(wsClient.fetch()).thenReturn(feed(200,3));fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);
        event("/news subscribe ws").andExpect(status().isNoContent());
        fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);verify(secondGroup,never()).sendBatch(anyList());
    }
    @Test void ordinaryTextPrivateEventsOldEventsAndOtherAtMentionsAreIgnored() throws Exception {
        event("Hello").andExpect(status().isNoContent());
        var json=mapper.readTree(body("/news subscribe ws"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)json).put("message_type","private");signed(mapper.writeValueAsBytes(json)).andExpect(status().isNoContent());
        ((com.fasterxml.jackson.databind.node.ObjectNode)json).put("message_type","group").put("time",Instant.now().minusSeconds(900).getEpochSecond());
        signed(mapper.writeValueAsBytes(json)).andExpect(status().isNoContent());
        byte[] other=mapper.writeValueAsBytes(Map.of("time",Instant.now().getEpochSecond(),"self_id",123,"group_id",99,"user_id",7,"message_id",999,
                "post_type","message","message_type","group","message",List.of(Map.of("type","at","data",Map.of("qq","999")),Map.of("type","text","data",Map.of("text","/news subscribe ws")))));
        signed(other).andExpect(status().isNoContent());verifyNoInteractions(qq);
    }
    @Test void signedMalformedOrOversizedPayloadsAreRejectedWithoutQqAccess() throws Exception {
        signed("{".getBytes()).andExpect(status().isBadRequest());
        byte[] huge=new byte[65537];signed(huge).andExpect(status().isPayloadTooLarge());verifyNoInteractions(qq);
    }
    @Test void statusReportsGroupModeWithoutExposingGroupIdsOrSecret() throws Exception {
        mvc.perform(get("/api/notifications/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupSubscriptionsEnabled").value(true))
                .andExpect(jsonPath("$.commandSecret").doesNotExist()).andExpect(jsonPath("$.groupId").doesNotExist());
    }
    @Test void legacySuccessfulAndPendingStampsAreCopiedOnlyToTheOriginalConfiguredGroup() throws Exception {
        baseline();
        var item=storage.execute(Game.WEISS_SCHWARZ,repos -> repos.news().findAll().get(0));
        storage.execute(Game.WEISS_SCHWARZ,repos -> {repos.subscriptions().deleteAll();repos.commands().deleteAll();
            var value=new QqNewsDelivery(item.getId(),WsRssHubNewsSource.SOURCE_ID);value.attempted(true,null,null);repos.deliveries().save(value);return null;});
        store.seedLegacy(Game.WEISS_SCHWARZ,42);
        assertThat(store.status(Game.WEISS_SCHWARZ).sentItems()).isEqualTo(1);
        assertThat(store.pending(new QqSubscriptionStore.Target(Game.WEISS_SCHWARZ,42))).isEmpty();
        assertThatThrownBy(()->store.seedLegacy(Game.WEISS_SCHWARZ,43)).hasMessageContaining("test group changed");
    }
    private void baseline() throws Exception {fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);verify(defaultGroup,never()).sendBatch(anyList());}
    @Test void cancelPendingThenResubscribeDoesNotReplayFailedMessages() throws Exception {
        baseline();event("/news subscribe ws");
        when(wsClient.fetch()).thenReturn(combinedFeed());
        doThrow(new QqNotificationException(QqNotificationException.Reason.UNAVAILABLE,"Unavailable")).when(secondGroup).sendBatch(anyList());
        fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);
        assertThat(store.pending(new QqSubscriptionStore.Target(Game.WEISS_SCHWARZ,99))).hasSize(3);
        event("/news unsubscribe ws");event("/news subscribe ws");
        doNothing().when(secondGroup).sendBatch(anyList());fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);
        verify(secondGroup,times(1)).sendBatch(anyList());
        assertThat(store.pending(new QqSubscriptionStore.Target(Game.WEISS_SCHWARZ,99))).isEmpty();
    }
    @Test void wsAndOcgSubscriptionsAreIndependentAndStatusHelpAreAvailableToMembers() throws Exception {
        event("/news subscribe ws");event("/news subscribe ocg");
        event("/news unsubscribe ws");
        assertThat(store.active(Game.WEISS_SCHWARZ,99)).isFalse();assertThat(store.active(Game.YUGIOH_OCG,99)).isTrue();
        when(qq.memberRole(anyLong(),anyLong())).thenReturn("member");
        event("/news status").andExpect(status().isNoContent());event("/news help").andExpect(status().isNoContent());
        verify(secondGroup).send(contains("游戏王 OCG：已订阅"));verify(secondGroup).send(QqGroupCommandService.HELP);
    }
    private org.springframework.test.web.servlet.ResultActions event(String text) throws Exception{return signed(body(text));}
    private org.springframework.test.web.servlet.ResultActions signed(byte[] body) throws Exception{return mvc.perform(post("/api/qq/events").contentType("application/json").content(body).header("X-Signature",sign(body)));}
    private byte[] body(String text) throws Exception{return mapper.writeValueAsBytes(Map.of("time",Instant.now().getEpochSecond(),"self_id",123,"group_id",99,"user_id",7,"message_id",-(++eventId),
            "post_type","message","message_type","group","message",List.of(Map.of("type","text","data",Map.of("text",text))),"sender",Map.of("role","owner")));}
    private String sign(byte[] body) throws Exception{var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec("synthetic-command-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA1"));return "sha1="+HexFormat.of().formatHex(mac.doFinal(body));}
    private byte[] feed(int id,int images){return xml(item(id,images));}
    private byte[] combinedFeed(){return xml(item(100,1)+item(200,3));}
    private byte[] xml(String items){return ("<rss version=\"2.0\"><channel><title>Local fixture</title><link>https://x.com/wstcg</link><description>Test</description>"+items+"</channel></rss>").getBytes(StandardCharsets.UTF_8);}
    private String item(int id,int images){return "<item><title>今日のカード synthetic</title><link>https://x.com/wstcg/status/"+id+"</link><description><![CDATA[今日のカード "+java.util.stream.IntStream.range(0,images)
            .mapToObj(n->"<img src=\"https://pbs.twimg.com/media/synthetic-"+id+"-"+n+".png\"/>").collect(java.util.stream.Collectors.joining())+"]]></description></item>";}
    private static Path directory(){try{return Files.createTempDirectory("tcg-group-test-");}catch(Exception e){throw new IllegalStateException(e);}}
    private static String url(String name){return "jdbc:h2:file:"+DIRECTORY.resolve(name).toString().replace('\\','/');}
}

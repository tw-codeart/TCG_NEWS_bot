package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.NotificationMessage;
import com.twcodeart.tcgnews.notification.qq.QqNotificationSender;
import com.twcodeart.tcgnews.source.ws.WsCardRevealSource;
import com.twcodeart.tcgnews.source.ws.WsInformationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:post-preview;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","rsshub.enabled=false","qq.enabled=true","qq.test-group-id=42"})
@AutoConfigureMockMvc
class QqPostPreviewApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private NewsItemRepository news;
    @MockitoBean private QqNotificationSender sender;
    @MockitoBean private WsCardRevealSource cards;
    @MockitoBean private WsInformationSource information;
    @BeforeEach void clear() { news.deleteAll(); }

    @Test void limitOneMeansOneWholePostWithAllFourImagesAndExcludesWebsiteRecords() throws Exception {
        savePost(Game.WEISS_SCHWARZ,100,Instant.parse("2026-10-09T03:00:00Z"),4);
        news.saveAndFlush(new NewsItem(Game.WEISS_SCHWARZ,NewsCategory.NEWS,"Later website item",null,null,null,
                "https://ws-tcg.com/information/test/","f".repeat(64)));
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ").param("limit","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posts").value(1))
                .andExpect(jsonPath("$.images").value(4)).andExpect(jsonPath("$.attempted").value(1))
                .andExpect(jsonPath("$.sent").value(1));
        var batch=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(sender).sendBatch(batch.capture());
        @SuppressWarnings("unchecked") List<NotificationMessage> items=batch.getValue();
        assertThat(items).hasSize(4).extracting(NotificationMessage::imageUrl).doesNotHaveDuplicates();
        assertThat(items).allMatch(item -> item.text().contains("今日のカード"));
        verifyNoInteractions(cards,information);
    }
    @Test void latestPostIsOrderedByPublicationInsteadOfAnOldPostIngestedLater() throws Exception {
        savePost(Game.WEISS_SCHWARZ,100,Instant.parse("2026-10-09T03:00:00Z"),2);
        savePost(Game.WEISS_SCHWARZ,90,Instant.parse("2026-10-06T03:00:00Z"),3);
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images").value(2));
        var batch=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(sender).sendBatch(batch.capture());
        @SuppressWarnings("unchecked") List<NotificationMessage> items=batch.getValue();
        assertThat(items).allMatch(item -> item.text().contains("/status/100"));
    }
    @Test void selectsOcgIndependentlyAndReportsAnEmptyGameWithoutSending() throws Exception {
        mvc.perform(post("/api/notifications/posts").param("game","YUGIOH_OCG"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posts").value(0)).andExpect(jsonPath("$.sent").value(0));
        verifyNoInteractions(sender);
        savePost(Game.YUGIOH_OCG,200,Instant.parse("2026-10-09T03:00:00Z"),13);
        savePost(Game.WEISS_SCHWARZ,100,Instant.parse("2026-10-10T03:00:00Z"),1);
        mvc.perform(post("/api/notifications/posts").param("game","YUGIOH_OCG"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images").value(13)).andExpect(jsonPath("$.sent").value(1));
    }
    @Test void forbidsArbitraryGroupsBadGamesAndUnboundedPacketRequestsBeforeSending() throws Exception {
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ").param("group_id","99"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/posts").param("game","UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ").param("limit","6"))
                .andExpect(status().isBadRequest());
        savePost(Game.WEISS_SCHWARZ,100,Instant.parse("2026-10-09T03:00:00Z"),251);
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                        "Selected posts exceed five forward packets; lower the post limit"));
        verifyNoInteractions(sender);
    }
    @Test void statusIdentifiesTheNewApiWithoutExposingTokensOrGroups() throws Exception {
        mvc.perform(get("/api/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.apiRevision").value("post-preview-v2"))
                .andExpect(jsonPath("$.pollingEnabled").value(false))
                .andExpect(jsonPath("$.qqEnabled").value(true))
                .andExpect(jsonPath("$.qqAdapter").value("napcat"))
                .andExpect(jsonPath("$.pollingIntervalSeconds").value(300))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.testGroupId").doesNotExist());
    }
    @Test void aSpecificStoredPostIdSelectsAllThreeImagesInsteadOfTheNewerSingleImagePost() throws Exception {
        try(var input=getClass().getResourceAsStream("/fixtures/ws-rsshub-three-images.xml")) {
            for(var item:new com.twcodeart.tcgnews.source.ws.WsRssHubFeedParser().parse(input.readAllBytes())) {
                news.saveAndFlush(item);
            }
        }
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images").value(1));
        clearInvocations(sender);
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ").param("postId","2108386969822846981"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images").value(3))
                .andExpect(jsonPath("$.posts").value(1)).andExpect(jsonPath("$.sent").value(1));
        var batch=org.mockito.ArgumentCaptor.forClass(List.class);verify(sender).sendBatch(batch.capture());
        assertThat(batch.getValue()).hasSize(3);
        verifyNoInteractions(cards,information);
    }

    @Test void postIdCannotInjectUrlsGroupsOrAnUnboundedSelection() throws Exception {
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ").param("postId","https://other.example/"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ").param("postId","123").param("limit","2"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ").param("postId","123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posts").value(0));
        verifyNoInteractions(sender);
    }

    private void savePost(Game game,int id,Instant date,int images) {
        String account=game==Game.WEISS_SCHWARZ?"wstcg":"YuGiOh_OCG_INFO";
        for(int n=0;n<images;n++) news.saveAndFlush(new NewsItem(game,NewsCategory.CARD_REVEAL,
                game==Game.WEISS_SCHWARZ?"今日のカード":"収録カード公開",null,null,null,date,
                "https://pbs.twimg.com/media/"+account+"-"+id+"-"+n+".png","https://x.com/"+account+"/status/"+id,
                String.format("%064x",id*1000+n)));
    }

    @Test void mergesThreeSelectedPostsIntoOneForwardPacketAndKeepsAllImages() throws Exception {
        savePost(Game.WEISS_SCHWARZ,100,Instant.parse("2026-10-09T03:00:00Z"),3);
        savePost(Game.WEISS_SCHWARZ,101,Instant.parse("2026-10-09T04:00:00Z"),4);
        savePost(Game.WEISS_SCHWARZ,102,Instant.parse("2026-10-09T05:00:00Z"),2);
        mvc.perform(post("/api/notifications/posts").param("game","WEISS_SCHWARZ").param("limit","3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posts").value(3))
                .andExpect(jsonPath("$.images").value(9)).andExpect(jsonPath("$.sent").value(1));
        var batch=org.mockito.ArgumentCaptor.forClass(List.class);verify(sender).sendBatch(batch.capture());
        assertThat(batch.getValue()).hasSize(9);
    }
}

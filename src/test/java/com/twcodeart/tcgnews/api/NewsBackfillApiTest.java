package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.notification.NewsBackfillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NewsBackfillController.class)
class NewsBackfillApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean NewsBackfillService service;

    @Test void previewsWithoutSendingAndConfirmsOnlyAServerIssuedSelection() throws Exception {
        UUID id=UUID.randomUUID();Instant now=Instant.parse("2026-10-18T10:00:00Z");
        when(service.preview(Game.WEISS_SCHWARZ,null,null,null)).thenReturn(new NewsBackfillService.Preview(
                id,Game.WEISS_SCHWARZ,Instant.parse("2026-10-10T15:00:00Z"),Instant.parse("2026-10-17T15:00:00Z"),now.plusSeconds(600),3,1,0,0,3,
                List.of(),List.of(),"CURRENT_FEED_AND_STORED_NEWS_ONLY","History may be incomplete"));
        when(service.confirm(id)).thenReturn(new NewsBackfillService.Confirmation(Game.WEISS_SCHWARZ,3,3,0,0,"QUEUED_FOR_NORMAL_DELIVERY"));
        mvc.perform(post("/api/notifications/backfill/preview").param("game","WEISS_SCHWARZ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.previewId").value(id.toString()))
                .andExpect(jsonPath("$.toQueue").value(3))
                .andExpect(jsonPath("$.from").value("2026-10-10T15:00:00Z"))
                .andExpect(jsonPath("$.until").value("2026-10-17T15:00:00Z"));
        mvc.perform(post("/api/notifications/backfill/confirm").param("previewId",id.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queued").value(3));
    }

    @Test void rejectsArbitraryGroupsBodiesAndInvalidGames() throws Exception {
        mvc.perform(post("/api/notifications/backfill/preview").param("game","WEISS_SCHWARZ").param("groupId","99"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/backfill/preview").param("game","WEISS_SCHWARZ").content("{\"urls\":[\"https://example.com\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/backfill/preview").param("game","UNKNOWN"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/backfill/confirm").param("previewId","not-a-preview"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void returnsReadableExpiredPreviewAndStorageErrors() throws Exception {
        UUID id=UUID.randomUUID();
        when(service.confirm(id)).thenThrow(new IllegalArgumentException("Preview is missing or expired; preview again before confirming"));
        mvc.perform(post("/api/notifications/backfill/confirm").param("previewId",id.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")))
                .andExpect(jsonPath("$.errorId").isNotEmpty());
        when(service.preview(Game.YUGIOH_OCG,2,null,null)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private detail"));
        mvc.perform(post("/api/notifications/backfill/preview").param("game","YUGIOH_OCG").param("days","2"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Backfill storage is unavailable; check the error ID"));
    }
}

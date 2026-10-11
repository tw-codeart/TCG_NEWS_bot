package com.twcodeart.tcgnews.storage;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.QqDeliveryStore;
import com.twcodeart.tcgnews.source.NewsSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"storage.separated-enabled=true","rsshub.enabled=false","qq.enabled=false","spring.jpa.hibernate.ddl-auto=update"})
@AutoConfigureMockMvc
class SeparatedGameStorageTest {
    static final Path DIRECTORY = directory();
    @Autowired GameStorage storage;
    @Autowired NewsQueryService query;
    @Autowired NewsIngestionService ingestion;
    @Autowired QqDeliveryStore deliveries;
    @Autowired MockMvc mvc;
    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry properties) {
        var legacy=LegacyStorageFixture.source(url("legacy"));
        LegacyStorageFixture.schema(legacy); LegacyStorageFixture.history(legacy);
        properties.add("spring.datasource.url",()->url("legacy"));
        properties.add("storage.ws-url",()->url("ws")); properties.add("storage.ocg-url",()->url("ocg"));
        properties.add("storage.backup-directory",()->DIRECTORY.resolve("backups").toString());
    }

    @Test
    void migrationRoutesQueriesWritesDedupAndDeliveryStateToTheCorrectPhysicalFile() throws Exception {
        assertThat(storage.isReady()).isTrue();
        assertThat(query.findNews(Game.WEISS_SCHWARZ)).hasSize(1).extracting(NewsItem::getId).containsExactly(101L);
        assertThat(query.findNews(Game.YUGIOH_OCG)).hasSize(1).extracting(NewsItem::getId).containsExactly(202L);
        assertThat(query.findNews(null)).hasSize(2);
        assertThat(query.findLatestXPosts(Game.YUGIOH_OCG,1).get(0)).hasSize(1);
        assertThat(deliveries.status("ws-jp-rsshub-card-reveals").sentItems()).isEqualTo(1);
        assertThat(deliveries.pending("ocg-jp-rsshub-card-reveals")).hasSize(1);
        var source=mock(NewsSource.class); when(source.getSourceId()).thenReturn("ocg-test"); when(source.getGame()).thenReturn(Game.YUGIOH_OCG);
        when(source.fetchNews()).thenAnswer(invocation -> List.of(new NewsItem(Game.YUGIOH_OCG,NewsCategory.CARD_REVEAL,
                "Synthetic new OCG",null,null,"https://pbs.twimg.com/media/new-ocg.png","https://x.com/YuGiOh_OCG_INFO/status/999", "c".repeat(64))));
        var saved=ingestion.fetchAndSaveWithSummary(source);
        assertThat(saved.savedItems()).hasSize(1); assertThat(saved.savedItems().get(0).getId()).isGreaterThan(202);
        assertThat(ingestion.fetchAndSaveWithSummary(source).duplicates()).isEqualTo(1);
        var w=new JdbcTemplate(LegacyStorageFixture.source(url("ws"))); var o=new JdbcTemplate(LegacyStorageFixture.source(url("ocg")));
        assertThat(w.queryForList("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='WRITE_DELAY'",String.class)).isNotEmpty().containsOnly("0");
        assertThat(o.queryForList("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='WRITE_DELAY'",String.class)).isNotEmpty().containsOnly("0");
        assertThat(w.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isEqualTo(1);
        assertThat(o.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isEqualTo(2);
        deliveries.capture("ocg-jp-rsshub-card-reveals",true);
        assertThat(deliveries.pending("ocg-jp-rsshub-card-reveals")).hasSize(2);
        deliveries.recordAttempt(deliveries.pending("ocg-jp-rsshub-card-reveals"),true,null,null);
        assertThat(deliveries.status("ocg-jp-rsshub-card-reveals").sentItems()).isEqualTo(2);
        assertThat(w.queryForObject("SELECT COUNT(*) FROM QQ_NEWS_DELIVERIES",Long.class)).isEqualTo(1);
        mvc.perform(get("/api/news").param("game","YUGIOH_OCG")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/status")).andExpect(status().isOk()).andExpect(jsonPath("$.storageMode").value("SEPARATED"));
        assertThat(new JdbcTemplate(LegacyStorageFixture.source(url("legacy"))).queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isEqualTo(2);
        assertThat(Files.exists(DIRECTORY.resolve("ws.mv.db"))).isTrue(); assertThat(Files.exists(DIRECTORY.resolve("ocg.mv.db"))).isTrue();
    }

    private static Path directory() { try { return Files.createTempDirectory("tcg-separated-"); } catch(Exception e) { throw new IllegalStateException(e); } }
    private static String url(String name) { return "jdbc:h2:file:"+DIRECTORY.resolve(name).toString().replace('\\','/'); }
}

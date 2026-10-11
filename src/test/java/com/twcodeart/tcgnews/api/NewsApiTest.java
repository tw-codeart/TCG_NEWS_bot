package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.news.NewsItemRepository;
import com.twcodeart.tcgnews.news.NewsQueryService;
import com.twcodeart.tcgnews.product.Product;
import com.twcodeart.tcgnews.source.NewsSourceException;
import com.twcodeart.tcgnews.source.ws.WsCardRevealSource;
import com.twcodeart.tcgnews.source.ws.WsCardRevealsParser;
import com.twcodeart.tcgnews.source.ws.WsInformationPageClient;
import com.twcodeart.tcgnews.source.ws.WsInformationParser;
import com.twcodeart.tcgnews.source.ws.WsInformationSource;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:news-api-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class NewsApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private NewsItemRepository repository;

    @MockitoBean
    private WsCardRevealSource cardSource;

    @MockitoBean
    private WsInformationSource informationSource;

    @MockitoSpyBean
    private NewsQueryService query;

    @BeforeEach
    void resetDatabaseAndSources() throws NewsSourceException {
        repository.deleteAll();
        when(cardSource.getSourceId()).thenReturn("ws-jp-card-reveals");
        when(informationSource.getSourceId()).thenReturn("ws-jp-information");
        when(cardSource.fetchNews()).thenReturn(List.of());
        when(informationSource.fetchNews()).thenReturn(List.of());
    }

    @Test
    void returnsAnEmptyJsonArrayForAnEmptyDatabase() throws Exception {
        mvc.perform(get("/api/news")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    void deliveryStatusReportsDisabledConfigurationWithoutCredentialsOrSending() throws Exception {
        mvc.perform(get("/api/notifications/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryRevision").value("durable-qq-v1"))
                .andExpect(jsonPath("$.pollingEnabled").value(false))
                .andExpect(jsonPath("$.qqConfigured").value(false))
                .andExpect(jsonPath("$.configurationProblem").value("QQ notifications are disabled"))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.testGroupId").doesNotExist());
    }

    @Test
    void returnsStoredNewsNewestFirstAndAcceptsTheGameFilter() throws Exception {
        NewsItem first = repository.saveAndFlush(news("first", "a".repeat(64)));
        NewsItem second = repository.saveAndFlush(news("second", "b".repeat(64)));

        mvc.perform(get("/api/news")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].id").value(first.getId()))
                .andExpect(jsonPath("$[0].title").value("second"))
                .andExpect(jsonPath("$[0].game").value("WEISS_SCHWARZ"))
                .andExpect(jsonPath("$[0].category").value("NEWS"))
                .andExpect(jsonPath("$[0].publishedDate").value("2026-09-29"))
                .andExpect(jsonPath("$[0].createdAt").isString());
        mvc.perform(get("/api/news").param("game", "WEISS_SCHWARZ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.getId()));
    }

    @Test
    void rejectsAnUnknownGameWithAJsonError() throws Exception {
        mvc.perform(get("/api/news").param("game", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid game filter"));
    }

    @Test
    void fetchesBothSourcesAndPersistsNothingOnAnIdenticalRepeatedFetch() throws Exception {
        when(cardSource.fetchNews()).thenAnswer(invocation -> cardFixture());
        when(informationSource.fetchNews()).thenAnswer(invocation -> informationFixture());
        int fetched = cardFixture().size() + informationFixture().size();

        mvc.perform(post("/api/fetch")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(fetched))
                .andExpect(jsonPath("$.saved").value(9))
                .andExpect(jsonPath("$.duplicates").value(fetched - 9))
                .andExpect(jsonPath("$.unprocessed").value(0))
                .andExpect(jsonPath("$.successfulSources").value(2))
                .andExpect(jsonPath("$.errors").isEmpty());
        mvc.perform(post("/api/fetch")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(fetched))
                .andExpect(jsonPath("$.saved").value(0))
                .andExpect(jsonPath("$.duplicates").value(fetched))
                .andExpect(jsonPath("$.unprocessed").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());
        assertThat(repository.count()).isEqualTo(9);
        mvc.perform(get("/api/news")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    void continuesWithOtherSourcesAndReportsTheFailedSource() throws Exception {
        when(cardSource.fetchNews()).thenThrow(new NewsSourceException("Private details", new IOException("Timed out")));
        when(informationSource.fetchNews()).thenAnswer(invocation -> informationFixture());

        mvc.perform(post("/api/fetch")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(6))
                .andExpect(jsonPath("$.saved").value(6))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.successfulSources").value(1))
                .andExpect(jsonPath("$.errors[0].sourceId").value("ws-jp-card-reveals"))
                .andExpect(jsonPath("$.errors[0].message").value("Source fetch failed"));
        assertThat(repository.count()).isEqualTo(6);
    }

    @Test
    void returnsBadGatewayWhenAllSourcesFail() throws Exception {
        when(cardSource.fetchNews()).thenThrow(new NewsSourceException("Card failure", new IOException("Timed out")));
        when(informationSource.fetchNews()).thenThrow(new IllegalStateException("Unexpected page"));

        mvc.perform(post("/api/fetch")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.fetched").value(0))
                .andExpect(jsonPath("$.saved").value(0))
                .andExpect(jsonPath("$.successfulSources").value(0))
                .andExpect(jsonPath("$.errors.length()").value(2));
        assertThat(repository.count()).isZero();
    }

    @Test
    void returnsACleanStorageErrorWithoutExposingInternalDetails() throws Exception {
        doThrow(new DataAccessResourceFailureException("Private database connection details"))
                .when(query).findNews(null);
        var result = mvc.perform(get("/api/news")).andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"message\":\"News storage is unavailable\"}"))
                .andExpect(jsonPath("$.errorId").isString()).andReturn();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(json.get("errorId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-ID"));
    }

    @Test
    void unexpectedFailureReturnsAnIdWithoutInternalDetails() throws Exception {
        doThrow(new IllegalStateException("Private server details")).when(query).findNews(null);
        var response = mvc.perform(get("/api/news")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorId").isString()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("Private server details");
    }

    private NewsItem news(String title, String hash) {
        return new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS, title, null,
                null, LocalDate.of(2026, 9, 29), null, "https://ws-tcg.com/information/" + title + "/", hash);
    }

    private List<NewsItem> cardFixture() throws IOException {
        Product product = new Product(Game.WEISS_SCHWARZ, "Test product", "https://ws-tcg.com/products/test/");
        try (InputStream fixture = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/ws-card-reveals-archive.html"))) {
            return new WsCardRevealsParser().parse(Jsoup.parse(fixture, "UTF-8", product.getDetailUrl()), product);
        }
    }

    private List<NewsItem> informationFixture() throws IOException {
        try (InputStream fixture = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/ws-information.html"))) {
            return new WsInformationParser().parse(
                    Jsoup.parse(fixture, "UTF-8", WsInformationPageClient.INFORMATION_URL));
        }
    }
}

package com.twcodeart.tcgnews.news;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NewsItemRepositoryTest {

    @Autowired
    private NewsItemRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsAndReloadsCardRevealWithCreationTimestamp() {
        NewsItem newsItem = new NewsItem(
                Game.WEISS_SCHWARZ,
                NewsCategory.CARD_REVEAL,
                "Test card reveal",
                "Test product",
                LocalDate.of(2026, 10, 4),
                "https://example.test/card.png",
                "https://example.test/products/test",
                "a".repeat(64));

        NewsItem saved = repository.saveAndFlush(newsItem);
        entityManager.clear();
        NewsItem reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getGame()).isEqualTo(Game.WEISS_SCHWARZ);
        assertThat(reloaded.getCategory()).isEqualTo(NewsCategory.CARD_REVEAL);
        assertThat(reloaded.getTitle()).isEqualTo(newsItem.getTitle());
        assertThat(reloaded.getProductName()).isEqualTo(newsItem.getProductName());
        assertThat(reloaded.getRevealDate()).isEqualTo(newsItem.getRevealDate());
        assertThat(reloaded.getImageUrl()).isEqualTo(newsItem.getImageUrl());
        assertThat(reloaded.getSourceUrl()).isEqualTo(newsItem.getSourceUrl());
        assertThat(reloaded.getContentHash()).isEqualTo(newsItem.getContentHash());
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void persistsInformationPublicationDateAndOptionalNullFields() {
        NewsItem information = new NewsItem(Game.WEISS_SCHWARZ, NewsCategory.NEWS,
                "Official notice", null, null, LocalDate.of(2026, 9, 29),
                null, "https://ws-tcg.com/information/test/", "b".repeat(64));
        NewsItem saved = repository.saveAndFlush(information);
        entityManager.clear();

        NewsItem reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCategory()).isEqualTo(NewsCategory.NEWS);
        assertThat(reloaded.getTitle()).isEqualTo("Official notice");
        assertThat(reloaded.getPublishedDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(reloaded.getRevealDate()).isNull();
        assertThat(reloaded.getImageUrl()).isNull();
        assertThat(reloaded.getProductName()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }
}

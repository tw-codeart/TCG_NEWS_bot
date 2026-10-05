package com.twcodeart.tcgnews.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    boolean existsByContentHash(String contentHash);

    boolean existsByGameAndCategoryAndRevealDateAndImageUrl(
            Game game, NewsCategory category, LocalDate revealDate, String imageUrl);

    boolean existsByGameAndCategoryAndSourceUrlAndPublishedDateAndTitle(
            Game game, NewsCategory category, String sourceUrl, LocalDate publishedDate, String title);
}

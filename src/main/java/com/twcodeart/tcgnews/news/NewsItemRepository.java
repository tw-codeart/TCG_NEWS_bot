package com.twcodeart.tcgnews.news;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    boolean existsByContentHash(String contentHash);

    List<NewsItem> findByGame(Game game, Sort sort);

    org.springframework.data.domain.Page<NewsItem> findByGame(Game game, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            SELECT N.sourceUrl FROM NewsItem N
            WHERE N.game=:game AND N.category=:category
              AND LOWER(SUBSTRING(N.sourceUrl,1,LENGTH(:prefix)))=LOWER(:prefix)
            GROUP BY N.sourceUrl
            ORDER BY MAX(N.publishedAt) DESC NULLS LAST, MAX(N.createdAt) DESC, MAX(N.id) DESC
            """)
    List<String> findLatestPostUrls(@org.springframework.data.repository.query.Param("game") Game game,
            @org.springframework.data.repository.query.Param("category") NewsCategory category,
            @org.springframework.data.repository.query.Param("prefix") String prefix,
            org.springframework.data.domain.Pageable pageable);

    List<NewsItem> findByGameAndCategoryAndSourceUrl(Game game, NewsCategory category, String sourceUrl, Sort sort);

    boolean existsByGameAndCategoryAndRevealDateAndImageUrl(
            Game game, NewsCategory category, LocalDate revealDate, String imageUrl);

    boolean existsByGameAndCategoryAndSourceUrlAndPublishedDateAndTitle(
            Game game, NewsCategory category, String sourceUrl, LocalDate publishedDate, String title);
}

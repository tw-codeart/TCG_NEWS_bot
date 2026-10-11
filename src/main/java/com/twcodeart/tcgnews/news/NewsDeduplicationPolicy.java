package com.twcodeart.tcgnews.news;

import org.springframework.stereotype.Component;

/**
 * Exact identity rules shared by all sources. URLs and thumbnails alone are not
 * globally unique announcements; additional signals must agree.
 */
@Component
public class NewsDeduplicationPolicy {

    private final NewsItemRepository repository;

    public NewsDeduplicationPolicy(NewsItemRepository repository) {
        this.repository = repository;
    }

    public boolean isDuplicate(NewsItem item) {
        return isDuplicate(item, repository);
    }

    public boolean isDuplicate(NewsItem item, NewsItemRepository repository) {
        if (repository.existsByContentHash(item.getContentHash())) {
            return true;
        }
        if (item.getCategory() == NewsCategory.CARD_REVEAL) {
            // A product page contains many cards. A shared image/date identifies
            // a reveal even when it appears on a different product page.
            return item.getRevealDate() != null && hasText(item.getImageUrl())
                    && repository.existsByGameAndCategoryAndRevealDateAndImageUrl(
                    item.getGame(), item.getCategory(), item.getRevealDate(), item.getImageUrl());
        }
        // Product/rules/card-list URLs and banner images can be reused. Require
        // the exact announcement title and date, never title similarity alone.
        // Spring Data matches a missing publication date with IS NULL.
        return hasText(item.getTitle())
                && repository.existsByGameAndCategoryAndSourceUrlAndPublishedDateAndTitle(
                item.getGame(), item.getCategory(), item.getSourceUrl(), item.getPublishedDate(), item.getTitle());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

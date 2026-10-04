package com.twcodeart.tcgnews.source;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItem;

import java.util.List;

/**
 * Fetches news from an external source without exposing its page structure.
 * Implementations handle retrieval and parsing; persistence, deduplication,
 * translation, and notifications belong outside this contract.
 */
public interface NewsSource {

    /**
     * Returns a stable, non-blank identifier for logging and source selection.
     * Each configured source must have a distinct identifier.
     */
    String getSourceId();

    /**
     * Returns the non-null game associated with this source.
     */
    Game getGame();

    /**
     * Fetches news without saving it or suppressing duplicate items.
     * All returned items must belong to the game returned by {@link #getGame()}.
     * The list and its elements must not be null. An empty list means a
     * successful fetch found no applicable news, not that retrieval failed.
     *
     * @return fetched news items that have not been persisted
     * @throws NewsSourceException if retrieval or parsing fails; implementations
     *         must preserve the original cause so callers can report the failure
     *         and continue with other sources
     */
    List<NewsItem> fetchNews() throws NewsSourceException;
}

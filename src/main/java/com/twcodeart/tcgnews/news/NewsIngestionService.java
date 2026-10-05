package com.twcodeart.tcgnews.news;

import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.NewsSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NewsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(NewsIngestionService.class);
    private final NewsItemRepository repository;
    private final NewsDeduplicationPolicy deduplicationPolicy;

    public NewsIngestionService(NewsItemRepository repository, NewsDeduplicationPolicy deduplicationPolicy) {
        this.repository = repository;
        this.deduplicationPolicy = deduplicationPolicy;
    }

    /**
     * Fetches one source and returns newly saved records. All identity checks use
     * the shared policy, outside parsers. This method does not wrap the batch in a transaction.
     */
    public List<NewsItem> fetchAndSave(NewsSource source) throws NewsSourceException {
        return fetchAndSaveWithSummary(source).savedItems();
    }

    public IngestionResult fetchAndSaveWithSummary(NewsSource source) throws NewsSourceException {
        List<NewsItem> fetched = source.fetchNews();
        List<NewsItem> saved = new ArrayList<>();
        int duplicates = 0;
        try {
            for (NewsItem item : fetched) {
                String hash = item.getContentHash();
                if (deduplicationPolicy.isDuplicate(item)) {
                    duplicates++;
                    log.debug("Skipping duplicate news: source={}, contentHash={}", source.getSourceId(), hash);
                    continue;
                }
                try {
                    saved.add(repository.saveAndFlush(item));
                } catch (DataIntegrityViolationException exception) {
                    // A concurrent fetch may have saved the same hash after the lookup.
                    if (deduplicationPolicy.isDuplicate(item)) {
                        duplicates++;
                        log.debug("Skipping concurrently saved news: source={}, contentHash={}",
                                source.getSourceId(), hash);
                    } else {
                        log.error("Failed to save news: source={}, sourceUrl={}, imageUrl={}",
                                source.getSourceId(), item.getSourceUrl(), item.getImageUrl(), exception);
                        throw exception;
                    }
                }
            }
        } catch (RuntimeException exception) {
            throw new IngestionFailure(new IngestionResult(fetched.size(), saved, duplicates), exception);
        }
        log.info("News fetch complete: source={}, fetched={}, saved={}",
                source.getSourceId(), fetched.size(), saved.size());
        return new IngestionResult(fetched.size(), saved, duplicates);
    }

    public record IngestionResult(int fetched, List<NewsItem> savedItems, int duplicates) {
        public IngestionResult {
            savedItems = List.copyOf(savedItems);
        }

        public int unprocessed() {
            return fetched - savedItems.size() - duplicates;
        }
    }

    public static class IngestionFailure extends RuntimeException {
        private final IngestionResult result;

        public IngestionFailure(IngestionResult result, RuntimeException cause) {
            super("News persistence failed after saving " + result.savedItems().size() + " items", cause);
            this.result = result;
        }

        public IngestionResult getResult() {
            return result;
        }
    }
}

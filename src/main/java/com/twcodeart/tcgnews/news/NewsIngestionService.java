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
        List<NewsItem> fetched = source.fetchNews();
        List<NewsItem> saved = new ArrayList<>();
        for (NewsItem item : fetched) {
            String hash = item.getContentHash();
            if (deduplicationPolicy.isDuplicate(item)) {
                log.debug("Skipping duplicate news: source={}, contentHash={}", source.getSourceId(), hash);
                continue;
            }
            try {
                saved.add(repository.saveAndFlush(item));
            } catch (DataIntegrityViolationException exception) {
                // A concurrent fetch may have saved the same hash after the lookup.
                if (deduplicationPolicy.isDuplicate(item)) {
                    log.debug("Skipping concurrently saved news: source={}, contentHash={}",
                            source.getSourceId(), hash);
                } else {
                    log.error("Failed to save news: source={}, sourceUrl={}, imageUrl={}",
                            source.getSourceId(), item.getSourceUrl(), item.getImageUrl(), exception);
                    throw exception;
                }
            }
        }
        log.info("News fetch complete: source={}, fetched={}, saved={}",
                source.getSourceId(), fetched.size(), saved.size());
        return saved;
    }
}

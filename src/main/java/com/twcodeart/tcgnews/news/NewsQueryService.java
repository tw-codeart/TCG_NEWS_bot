package com.twcodeart.tcgnews.news;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private final NewsItemRepository repository;

    public NewsQueryService(NewsItemRepository repository) {
        this.repository = repository;
    }

    public List<NewsItem> findNews(Game game) {
        return game == null ? repository.findAll(NEWEST_FIRST) : repository.findByGame(game, NEWEST_FIRST);
    }

    public List<NewsItem> findLatestNews(int limit) {
        return repository.findAll(PageRequest.of(0, limit, NEWEST_FIRST)).getContent();
    }

    public List<NewsItem> findLatestNews(int limit, Game game) {
        return game == null ? findLatestNews(limit)
                : repository.findByGame(game, PageRequest.of(0, limit, NEWEST_FIRST)).getContent();
    }

    public List<List<NewsItem>> findLatestXPosts(Game game, int limit) {
        String prefix = switch (game) {
            case WEISS_SCHWARZ -> "https://x.com/wstcg/status/";
            case YUGIOH_OCG -> "https://x.com/YuGiOh_OCG_INFO/status/";
        };
        return repository.findLatestPostUrls(game, NewsCategory.CARD_REVEAL, prefix, PageRequest.of(0,limit))
                .stream().map(url -> {
                    var records = repository.findByGameAndCategoryAndSourceUrl(game, NewsCategory.CARD_REVEAL,
                            url, Sort.by(Sort.Direction.ASC,"id"));
                    var images = records.stream().filter(item -> item.getImageUrl()!=null && !item.getImageUrl().isBlank()).toList();
                    // An old image-free placeholder is not another image once real images exist.
                    return images.isEmpty() ? records.stream().limit(1).toList() : images;
                }).toList();
    }
}

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
}

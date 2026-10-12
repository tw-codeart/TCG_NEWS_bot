package com.twcodeart.tcgnews.news;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private final NewsItemRepository repository;
    private final com.twcodeart.tcgnews.storage.GameStorage storage;

    public NewsQueryService(NewsItemRepository repository) {
        this.repository = repository; this.storage = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NewsQueryService(NewsItemRepository repository,
            org.springframework.beans.factory.ObjectProvider<com.twcodeart.tcgnews.storage.GameStorage> storage) {
        this.repository = repository; this.storage = storage.getIfAvailable();
    }

    public List<NewsItem> findNews(Game game) {
        if (separated()) {
            if (game != null) return inGame(game, repo -> repo.findByGame(game, NEWEST_FIRST));
            return merge(inGame(Game.WEISS_SCHWARZ, repo -> repo.findAll(NEWEST_FIRST)),
                    inGame(Game.YUGIOH_OCG, repo -> repo.findAll(NEWEST_FIRST)));
        }
        return game == null ? repository.findAll(NEWEST_FIRST) : repository.findByGame(game, NEWEST_FIRST);
    }

    public List<NewsItem> findLatestNews(int limit) {
        if (separated()) return merge(findLatestNews(limit, Game.WEISS_SCHWARZ), findLatestNews(limit, Game.YUGIOH_OCG))
                .stream().limit(limit).toList();
        return repository.findAll(PageRequest.of(0, limit, NEWEST_FIRST)).getContent();
    }

    public List<NewsItem> findLatestNews(int limit, Game game) {
        if (separated() && game != null) return inGame(game,
                repo -> repo.findByGame(game, PageRequest.of(0,limit,NEWEST_FIRST)).getContent());
        return game == null ? findLatestNews(limit)
                : repository.findByGame(game, PageRequest.of(0, limit, NEWEST_FIRST)).getContent();
    }

    public List<List<NewsItem>> findLatestXPosts(Game game, int limit) {
        return com.twcodeart.tcgnews.notification.RssHubNotificationSources.forGame(game).stream()
                .flatMap(topic -> inGame(game, repo -> repo.findLatestPostUrls(game, NewsCategory.CARD_REVEAL,
                        topic.prefix(), PageRequest.of(0,limit))).stream())
                .map(url -> postRecords(game,url)).sorted(java.util.Comparator.comparing(
                        (List<NewsItem> post) -> post.stream().map(NewsItem::getPublishedAt).filter(java.util.Objects::nonNull)
                                .max(java.util.Comparator.naturalOrder()).orElse(null),
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                        .thenComparing(post -> post.stream().map(NewsItem::getCreatedAt).max(java.util.Comparator.naturalOrder()).orElseThrow(),java.util.Comparator.reverseOrder()))
                .limit(limit).toList();
    }

    public List<List<NewsItem>> findXPost(Game game,String postId) {
        return com.twcodeart.tcgnews.notification.RssHubNotificationSources.forGame(game).stream()
                .map(topic->postRecords(game,topic.prefix()+postId)).filter(records->!records.isEmpty()).toList();
    }

    private String xPrefix(Game game) {
        return switch(game) {
            case WEISS_SCHWARZ -> "https://x.com/wstcg/status/";
            case YUGIOH_OCG -> "https://x.com/YuGiOh_OCG_INFO/status/";
        };
    }

    private List<NewsItem> postRecords(Game game,String url) {
        var records=inGame(game, repo -> repo.findByGameAndCategoryAndSourceUrl(game,NewsCategory.CARD_REVEAL,
                url,Sort.by(Sort.Direction.ASC,"id")));
        var images=records.stream().filter(item -> item.getImageUrl()!=null && !item.getImageUrl().isBlank()).toList();
        return images.isEmpty() ? records.stream().limit(1).toList() : images;
    }

    private boolean separated() {
        if (storage != null) storage.requireReady();
        return storage != null && storage.isSeparated();
    }
    private <T> T inGame(Game game, java.util.function.Function<NewsItemRepository,T> work) {
        return separated() ? storage.execute(game, repositories -> work.apply(repositories.news())) : work.apply(repository);
    }
    private List<NewsItem> merge(List<NewsItem> ws, List<NewsItem> ocg) {
        return java.util.stream.Stream.concat(ws.stream(),ocg.stream()).sorted(
                java.util.Comparator.comparing(NewsItem::getCreatedAt).reversed()
                        .thenComparing(NewsItem::getId, java.util.Comparator.reverseOrder()).thenComparing(NewsItem::getGame)).toList();
    }
}

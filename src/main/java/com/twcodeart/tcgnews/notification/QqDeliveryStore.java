package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsCategory;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import com.twcodeart.tcgnews.storage.GameRepositories;
import com.twcodeart.tcgnews.storage.GameStorage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class QqDeliveryStore {
    private final GameStorage storage;
    public QqDeliveryStore(GameStorage storage) { this.storage = storage; }

    public void prepare(String sourceId) {
        storage.execute(game(sourceId), repositories -> {
            if (!repositories.cursors().existsById(sourceId)) {
                repositories.cursors().saveAndFlush(new QqDeliveryCursor(sourceId, latestId(sourceId, repositories)));
            }
            return null;
        });
    }

    public void capture(String sourceId, boolean completedNonemptySnapshot) {
        storage.execute(game(sourceId), repositories -> {
            var cursor = repositories.cursors().findById(sourceId)
                    .orElseThrow(() -> new IllegalStateException("QQ delivery cursor not prepared"));
            if (!repositories.bootstraps().existsById(sourceId)) {
                if (completedNonemptySnapshot) {
                    repositories.bootstraps().save(new SourceNotificationBootstrap(sourceId));
                    cursor.advance(latestId(sourceId, repositories));
                }
                return null;
            }
            // Queue and cursor commit together in the selected game's transaction.
            var items = repositories.news().findQqDeliveryCandidates(game(sourceId), NewsCategory.CARD_REVEAL,
                    prefix(sourceId), cursor.getLastNewsId(), PageRequest.of(0, 500));
            for (var item : items) {
                if (!repositories.deliveries().existsById(item.getId())) {
                    repositories.deliveries().save(new QqNewsDelivery(item.getId(), sourceId));
                }
                cursor.advance(item.getId());
            }
            return null;
        });
    }

    public List<NewsItem> pending(String sourceId) {
        return storage.execute(game(sourceId), repositories -> {
            var ids = repositories.deliveries().findBySourceIdAndPendingTrueOrderByNewsIdAsc(sourceId, PageRequest.of(0, 500))
                    .stream().map(QqNewsDelivery::getNewsId).toList();
            return repositories.news().findAllById(ids).stream().sorted(java.util.Comparator.comparing(NewsItem::getId)).toList();
        });
    }

    public void recordAttempt(List<NewsItem> items, boolean success, String errorId, String reason) {
        if (items.isEmpty()) return;
        Game game = items.get(0).getGame();
        if (items.stream().anyMatch(item -> item.getGame() != game)) throw new IllegalArgumentException("Delivery packet mixes games");
        storage.execute(game, repositories -> {
            for (var item : items) repositories.deliveries().findById(item.getId()).orElseThrow().attempted(success, errorId, reason);
            return null;
        });
    }

    public SourceStatus status(String sourceId) {
        return storage.execute(game(sourceId), repositories -> {
            var deliveries = repositories.deliveries();
            var last = deliveries.findFirstBySourceIdAndLastAttemptAtIsNotNullOrderByLastAttemptAtDescNewsIdDesc(sourceId).orElse(null);
            var sent = deliveries.findFirstBySourceIdAndSentAtIsNotNullOrderBySentAtDescNewsIdDesc(sourceId).orElse(null);
            return new SourceStatus(sourceId, repositories.bootstraps().existsById(sourceId), repositories.cursors().existsById(sourceId),
                    deliveries.countBySourceIdAndPending(sourceId, true), deliveries.countBySourceIdAndPending(sourceId, false),
                    last == null ? null : last.getLastAttemptAt(), sent == null ? null : sent.getSentAt(),
                    last == null ? null : last.getLastErrorId(), last == null ? null : last.getLastErrorReason());
        });
    }

    private long latestId(String sourceId, GameRepositories repositories) {
        return repositories.news().findLatestQqDeliveryId(game(sourceId), NewsCategory.CARD_REVEAL, prefix(sourceId));
    }
    private Game game(String id) {
        if (WsRssHubNewsSource.SOURCE_ID.equals(id)) return Game.WEISS_SCHWARZ;
        if (OcgRssHubNewsSource.SOURCE_ID.equals(id)) return Game.YUGIOH_OCG;
        throw new IllegalArgumentException("Unsupported RSSHub notification source");
    }
    private String prefix(String id) {
        return game(id) == Game.WEISS_SCHWARZ ? "https://x.com/wstcg/status/" : "https://x.com/YuGiOh_OCG_INFO/status/";
    }
    public record SourceStatus(String sourceId, boolean bootstrapComplete, boolean cursorInitialized,
                               long pendingItems, long sentItems, Instant lastAttemptAt, Instant lastSentAt,
                               String lastErrorId, String lastErrorReason) { }
}
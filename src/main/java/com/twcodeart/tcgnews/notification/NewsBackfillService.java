package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.qq.QqProperties;
import com.twcodeart.tcgnews.storage.GameRepositories;
import com.twcodeart.tcgnews.storage.GameStorage;
import com.twcodeart.tcgnews.subscription.QqGroupDelivery;
import com.twcodeart.tcgnews.subscription.QqGroupSubscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** Explicit recovery of stored/feed history into the configured test group's durable queue. */
@Service
public class NewsBackfillService {
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");
    private static final int MAX_ITEMS = 1000;
    private final NewsFetchService fetch;
    private final GameStorage storage;
    private final QqProperties qq;
    private final Clock clock;
    private final Map<UUID, Selection> previews = new LinkedHashMap<>();

    @Autowired
    public NewsBackfillService(NewsFetchService fetch, GameStorage storage, QqProperties qq) {
        this(fetch, storage, qq, Clock.systemUTC());
    }

    public NewsBackfillService(NewsFetchService fetch, GameStorage storage, QqProperties qq, Clock clock) {
        this.fetch = fetch; this.storage = storage; this.qq = qq; this.clock = clock;
    }

    public synchronized Preview preview(Game game, Integer days, LocalDate from, LocalDate through) {
        if (game == null) throw new IllegalArgumentException("Choose WEISS_SCHWARZ or YUGIOH_OCG");
        long group = qq.groupId();
        var window = window(days, from, through);
        var sources = fetch.refreshForBackfill(game);
        var items = storage.execute(game, repos -> {
            var matches = new ArrayList<NewsItem>();
            for (var source : sources) {
                // A fresh empty/failed feed must not silently become a completed baseline.
                if (!repos.bootstraps().existsById(source.sourceId())) continue;
                matches.addAll(repos.news().findBackfillCandidates(game, NewsCategory.CARD_REVEAL,
                        RssHubNotificationSources.topic(source.sourceId()).prefix(), window.from(), window.until(),
                        PageRequest.of(0, MAX_ITEMS + 1)));
            }
            if (matches.size() > MAX_ITEMS) throw new IllegalArgumentException("More than 1000 records; choose a shorter date range");
            return matches.stream().sorted(Comparator.comparing(NewsItem::getPublishedAt).thenComparing(NewsItem::getId)).toList();
        });
        Instant expires = clock.instant().plus(Duration.ofMinutes(10));
        previews.values().removeIf(value -> !value.expires().isAfter(clock.instant()));
        if (previews.size() >= 10) previews.remove(previews.keySet().iterator().next());
        UUID id = UUID.randomUUID();
        var selection = new Selection(game, group, qq.isGroupSubscriptionsEnabled(), window,
                items.stream().map(NewsItem::getId).toList(), expires);
        previews.put(id, selection);
        var counts = storage.execute(game, repos -> count(repos, selection));
        var posts = new LinkedHashMap<String, List<NewsItem>>();
        for (var item : items) posts.computeIfAbsent(item.getSourceUrl(), ignored -> new ArrayList<>()).add(item);
        return new Preview(id, game, window.from(), window.until(), expires, items.size(), posts.size(),
                counts.sent(), counts.pending(), counts.available(), sources,
                posts.values().stream().map(post -> new Post(post.get(0).getTitle(), post.get(0).getSourceUrl(),
                        post.get(0).getPublishedAt(), post.size())).toList(),
                "CURRENT_FEED_AND_STORED_NEWS_ONLY",
                "Feed history may be incomplete. Deleted delivery stamps cannot be recovered; previously sent history may repeat.");
    }

    public synchronized Confirmation confirm(UUID previewId) {
        var selection = previews.get(previewId);
        if (selection == null || !selection.expires().isAfter(clock.instant())) {
            previews.remove(previewId);
            throw new IllegalArgumentException("Preview is missing or expired; preview again before confirming");
        }
        qq.requireConfigured();
        if (qq.groupId() != selection.group() || qq.isGroupSubscriptionsEnabled() != selection.groupMode())
            throw new IllegalArgumentException("Delivery target changed; preview again");
        return storage.execute(selection.game(), repos -> {
            if (selection.groupMode() && !repos.subscriptions().findById(new QqGroupSubscription.Id(selection.game(), selection.group()))
                    .map(QqGroupSubscription::isActive).orElse(false))
                throw new IllegalArgumentException("Configured test group is not subscribed; subscribe before backfilling");
            var before = count(repos, selection);
            for (Long id : selection.ids()) {
                var item = repos.news().findById(id).orElseThrow(() -> new IllegalArgumentException("Stored selection changed; preview again"));
                validate(item, selection);
                String sourceId = RssHubNotificationSources.forGame(selection.game()).stream()
                        .filter(topic -> item.getSourceUrl().toLowerCase(Locale.ROOT).startsWith(topic.prefix().toLowerCase(Locale.ROOT)))
                        .map(RssHubNotificationSources.Topic::id).findFirst().orElseThrow();
                if (!repos.bootstraps().existsById(sourceId)) throw new IllegalArgumentException("Source baseline is not ready; preview again");
                if (selection.groupMode()) {
                    var key = new QqGroupDelivery.Id(selection.game(), selection.group(), id);
                    if (!repos.groupDeliveries().existsById(key))
                        repos.groupDeliveries().save(new QqGroupDelivery(selection.game(), selection.group(), id));
                } else if (!repos.deliveries().existsById(id)) {
                    repos.deliveries().save(new QqNewsDelivery(id, sourceId));
                }
            }
            // No network call in this transaction. Normal polling drains the existing bounded queue.
            return new Confirmation(selection.game(), selection.ids().size(), before.available(), before.pending(), before.sent(),
                    "QUEUED_FOR_NORMAL_DELIVERY");
        });
    }

    private Counts count(GameRepositories repos, Selection selection) {
        int sent = 0, pending = 0;
        for (Long id : selection.ids()) {
            if (selection.groupMode()) {
                var value = repos.groupDeliveries().findById(new QqGroupDelivery.Id(selection.game(), selection.group(), id)).orElse(null);
                if (value != null) { if (value.isPending()) pending++; else sent++; }
            } else {
                var value = repos.deliveries().findById(id).orElse(null);
                if (value != null) { if (value.isPending()) pending++; else sent++; }
            }
        }
        return new Counts(sent, pending, selection.ids().size() - sent - pending);
    }

    private void validate(NewsItem item, Selection selection) {
        if (item.getGame() != selection.game() || item.getCategory() != NewsCategory.CARD_REVEAL
                || item.getPublishedAt() == null || item.getPublishedAt().isBefore(selection.window().from())
                || !item.getPublishedAt().isBefore(selection.window().until()))
            throw new IllegalArgumentException("Stored selection changed; preview again");
    }

    private Window window(Integer days, LocalDate from, LocalDate through) {
        Instant now = clock.instant();
        if (from != null || through != null) {
            if (days != null || from == null || through == null || through.isBefore(from)
                    || through.isAfter(LocalDate.now(clock.withZone(JAPAN)))
                    || java.time.temporal.ChronoUnit.DAYS.between(from, through) >= 7)
                throw new IllegalArgumentException("Use both from/through dates in Japan time, at most seven days, without days");
            Instant end = through.plusDays(1).atStartOfDay(JAPAN).toInstant();
            return new Window(from.atStartOfDay(JAPAN).toInstant(), end.isAfter(now) ? now : end);
        }
        int length = days == null ? 7 : days;
        if (length < 1 || length > 7) throw new IllegalArgumentException("days must be between 1 and 7");
        LocalDate today = LocalDate.now(clock.withZone(JAPAN));
        return new Window(today.minusDays(length).atStartOfDay(JAPAN).toInstant(), today.atStartOfDay(JAPAN).toInstant());
    }

    private record Window(Instant from, Instant until) { }
    private record Counts(int sent, int pending, int available) { }
    private record Selection(Game game, long group, boolean groupMode, Window window, List<Long> ids, Instant expires) { }
    public record Post(String title, String sourceUrl, Instant publishedAt, int records) { }
    public record Preview(UUID previewId, Game game, Instant from, Instant until, Instant expiresAt,
                          int records, int posts, int alreadySent, int alreadyPending, int toQueue,
                          List<NewsFetchService.BackfillSource> sources, List<Post> entries, String coverage, String warning) { }
    public record Confirmation(Game game, int records, int queued, int alreadyPending, int alreadySent, String state) { }
}

package com.twcodeart.tcgnews.operations;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.storage.GameStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Only replay markers older than the five-minute callback acceptance window are disposable. */
@Service
@EnableScheduling
public class CommandRetentionService {
    private static final Logger log = LoggerFactory.getLogger(CommandRetentionService.class);
    private final GameStorage storage;
    private final RetentionProperties properties;
    private volatile Snapshot snapshot = new Snapshot(null, 0, 0);

    public CommandRetentionService(GameStorage storage, RetentionProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 86400000)
    public void clean() { cleanAt(Instant.now()); }

    public void cleanAt(Instant now) {
        if (!properties.isEnabled() || !storage.isReady()) return;
        Instant cutoff = now.minus(properties.getCommandDays(), ChronoUnit.DAYS);
        int deleted = 0, failures = 0;
        for (var game : Game.values()) {
            // Shared mode has a single command table; never clean it twice.
            if (game == Game.YUGIOH_OCG && !storage.isSeparated()) continue;
            try {
                deleted += storage.execute(game, repos -> repos.commands().deleteExpired(cutoff));
            } catch (RuntimeException exception) {
                failures++;
                log.error("Command retention failed: game={}, errorId={}", game,
                        com.twcodeart.tcgnews.diagnostics.DiagnosticIds.current(), exception);
            }
        }
        snapshot = new Snapshot(now, deleted, failures);
        log.info("Command retention complete: deleted={}, failures={}; news and delivery stamps retained", deleted, failures);
    }

    public Snapshot snapshot() { return snapshot; }
    public record Snapshot(Instant lastRunAt, int deletedCommands, int failedGames) { }
}

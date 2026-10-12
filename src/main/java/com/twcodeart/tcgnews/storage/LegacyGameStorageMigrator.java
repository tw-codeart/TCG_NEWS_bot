package com.twcodeart.tcgnews.storage;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;

/** Retains the legacy DB; copies each game's rows and delivery state in one destination transaction. */
public class LegacyGameStorageMigrator {
    private static final Logger log = LoggerFactory.getLogger(LegacyGameStorageMigrator.class);
    private static final String MARKER = "game-split-v1";
    private static final List<String> TABLES = List.of("NEWS_ITEMS", "PRODUCTS", "SOURCE_NOTIFICATION_BOOTSTRAPS",
            "QQ_DELIVERY_CURSORS", "QQ_NEWS_DELIVERIES");

    public void migrate(String legacyUrl, String username, String password, Path backupDirectory,
                        Map<Game, DataSource> destinations) throws Exception {
        for (var game : Game.values()) initializeMarkerTable(destinations.get(game));
        if (Arrays.stream(Game.values()).allMatch(game -> marked(destinations.get(game)))) {
            validateScopes(destinations);
            return;
        }
        for (var game : Game.values()) {
            if (!marked(destinations.get(game)) && !empty(destinations.get(game))) {
                throw new IllegalStateException("Refusing to merge an unmarked nonempty game database: " + game);
            }
        }
        Path sourceFile = H2FileLocations.databaseFile(legacyUrl);
        if (sourceFile == null) throw new IllegalArgumentException("Legacy migration requires an H2 file URL");
        Map<String, List<Map<String,Object>>> snapshot = new LinkedHashMap<>();
        for (String table : TABLES) snapshot.put(table, List.of());
        if (Files.exists(sourceFile)) {
            try (var connection = DriverManager.getConnection(legacyUrl + ";IFEXISTS=TRUE", username, password)) {
                var source = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                Files.createDirectories(backupDirectory);
                Path backup = backupDirectory.resolve("legacy-" + UUID.randomUUID() + ".zip").toAbsolutePath();
                source.execute("BACKUP TO '" + backup.toString().replace("'", "''") + "'");
                if (!Files.isRegularFile(backup) || Files.size(backup) == 0) throw new IllegalStateException("Legacy backup was not created");
                for (String table : TABLES) {
                    if (source.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=CURRENT_SCHEMA() AND TABLE_NAME=?",
                            Long.class, table) > 0) snapshot.put(table, source.queryForList("SELECT * FROM " + table));
                }
                log.info("Legacy game-storage backup created; source rows remain intact");
            }
        }
        validateSnapshot(snapshot);
        for (var game : Game.values()) {
            var destination = destinations.get(game);
            if (marked(destination)) continue;
            var jdbc = new JdbcTemplate(destination);
            new TransactionTemplate(new DataSourceTransactionManager(destination)).executeWithoutResult(status -> {
                for (String table : TABLES) {
                    for (var row : snapshot.get(table)) if (rowGame(table, row, snapshot) == game) copyRow(jdbc, table, row);
                }
                jdbc.update("INSERT INTO GAME_STORAGE_MIGRATIONS (MIGRATION_ID) VALUES (?)", MARKER);
            });
            log.info("Legacy game storage copied: game={}, newsRows={}", game, jdbc.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS", Long.class));
        }
        // Identity changes and constraint DDL happen only after committed row copies, and are safe to rerun.
        validateScopes(destinations);
    }

    private void initializeMarkerTable(DataSource destination) {
        new JdbcTemplate(destination).execute("CREATE TABLE IF NOT EXISTS GAME_STORAGE_MIGRATIONS (MIGRATION_ID VARCHAR(64) PRIMARY KEY)");
    }
    private boolean marked(DataSource source) {
        return new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM GAME_STORAGE_MIGRATIONS WHERE MIGRATION_ID=?", Long.class, MARKER) > 0;
    }
    private boolean empty(DataSource source) {
        var jdbc = new JdbcTemplate(source);
        return TABLES.stream().allMatch(table -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class) == 0);
    }
    private void validateSnapshot(Map<String,List<Map<String,Object>>> rows) {
        for (String table : TABLES) for (var row : rows.get(table)) rowGame(table, row, rows);
    }
    private Game rowGame(String table, Map<String,Object> row, Map<String,List<Map<String,Object>>> snapshot) {
        if (table.equals("NEWS_ITEMS") || table.equals("PRODUCTS")) return Game.valueOf(row.get("GAME").toString());
        String source = Objects.toString(row.get("SOURCE_ID"), "");
        Game game;
        if (WsRssHubNewsSource.SOURCE_ID.equals(source)) game = Game.WEISS_SCHWARZ;
        else if (OcgRssHubNewsSource.SOURCE_ID.equals(source)
                ||com.twcodeart.tcgnews.source.ocg.YuGiOhJpRssHubNewsSource.SOURCE_ID.equals(source)) game = Game.YUGIOH_OCG;
        else throw new IllegalStateException("Unknown source ID in legacy notification state");
        if (table.equals("QQ_NEWS_DELIVERIES")) {
            long id = ((Number)row.get("NEWS_ID")).longValue();
            var item = snapshot.get("NEWS_ITEMS").stream().filter(news -> ((Number)news.get("ID")).longValue() == id).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Legacy delivery refers to missing news"));
            if (!game.name().equals(item.get("GAME").toString())) throw new IllegalStateException("Legacy delivery/source game mismatch");
        }
        return game;
    }
    private void copyRow(JdbcTemplate jdbc, String table, Map<String,Object> row) {
        var columns = jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=CURRENT_SCHEMA() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION",
                String.class, table).stream().filter(row::containsKey).toList();
        // Missing optional columns in early project versions retain destination defaults/nulls.
        String names = String.join(",", columns);
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        jdbc.update("INSERT INTO " + table + " (" + names + ") VALUES (" + placeholders + ")", columns.stream().map(row::get).toArray());
    }
    private void validateScopes(Map<Game,DataSource> destinations) {
        for (var game : Game.values()) {
            var jdbc = new JdbcTemplate(destinations.get(game));
            for (String table : List.of("NEWS_ITEMS", "PRODUCTS","QQ_GROUP_SUBSCRIPTIONS","QQ_GROUP_DELIVERIES")) {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=CURRENT_SCHEMA() AND TABLE_NAME=?",Long.class,table)==0)continue;
                if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE GAME<>?", Long.class, game.name()) > 0) {
                    throw new IllegalStateException("Wrong-game records found in separated database: " + game);
                }
                jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT IF NOT EXISTS " + table + "_GAME_STORAGE_SCOPE CHECK (GAME='" + game.name() + "')");
            }
            long maximum = jdbc.queryForObject("SELECT COALESCE(MAX(ID),0) FROM NEWS_ITEMS", Long.class);
            // Never move the identity backwards on subsequent startups.
            if (jdbc.queryForObject("SELECT COUNT(*) FROM GAME_STORAGE_MIGRATIONS WHERE MIGRATION_ID='identity-v1'", Long.class) == 0) {
                jdbc.execute("ALTER TABLE NEWS_ITEMS ALTER COLUMN ID RESTART WITH " + (maximum + 1));
                jdbc.update("INSERT INTO GAME_STORAGE_MIGRATIONS (MIGRATION_ID) VALUES ('identity-v1')");
            }
        }
    }
}

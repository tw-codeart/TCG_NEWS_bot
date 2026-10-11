package com.twcodeart.tcgnews.storage;

import com.twcodeart.tcgnews.news.Game;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class LegacyGameStorageMigratorTest {
    @TempDir Path directory;

    @Test
    void partitionsRowsPreservesStampsAndPendingStateAndLeavesTheLegacyDatabaseIntact() throws Exception {
        String legacy = url("legacy");
        DataSource source = LegacyStorageFixture.source(legacy), ws = destination("ws"), ocg = destination("ocg");
        LegacyStorageFixture.schema(source); LegacyStorageFixture.history(source);
        var before = new JdbcTemplate(source).queryForList("SELECT * FROM NEWS_ITEMS ORDER BY ID");
        var migrator = new LegacyGameStorageMigrator();
        migrator.migrate(legacy,"sa","",directory.resolve("backups"),Map.of(Game.WEISS_SCHWARZ,ws,Game.YUGIOH_OCG,ocg));
        var w = new JdbcTemplate(ws); var o = new JdbcTemplate(ocg);
        assertThat(w.queryForList("SELECT GAME FROM NEWS_ITEMS",String.class)).containsExactly("WEISS_SCHWARZ");
        assertThat(o.queryForList("SELECT GAME FROM NEWS_ITEMS",String.class)).containsExactly("YUGIOH_OCG");
        assertThat(w.queryForObject("SELECT PENDING FROM QQ_NEWS_DELIVERIES WHERE NEWS_ID=101",Boolean.class)).isFalse();
        assertThat(o.queryForObject("SELECT LAST_ERROR_REASON FROM QQ_NEWS_DELIVERIES WHERE NEWS_ID=202",String.class)).isEqualTo("API_FAILURE");
        assertThat(w.queryForObject("SELECT SENT_AT FROM QQ_NEWS_DELIVERIES",Object.class))
                .isEqualTo(new JdbcTemplate(source).queryForObject("SELECT SENT_AT FROM QQ_NEWS_DELIVERIES WHERE NEWS_ID=101",Object.class));
        assertThat(o.queryForObject("SELECT LAST_NEWS_ID FROM QQ_DELIVERY_CURSORS",Long.class)).isEqualTo(202);
        assertThat(w.queryForObject("SELECT COUNT(*) FROM PRODUCTS",Long.class)).isEqualTo(1);
        assertThat(o.queryForObject("SELECT COUNT(*) FROM PRODUCTS",Long.class)).isZero();
        assertThat(new JdbcTemplate(source).queryForList("SELECT * FROM NEWS_ITEMS ORDER BY ID")).isEqualTo(before);
        try(var backups=Files.list(directory.resolve("backups"))) { assertThat(backups.toList()).hasSize(1); }

        o.update("UPDATE QQ_NEWS_DELIVERIES SET PENDING=FALSE,SENT_AT=CURRENT_TIMESTAMP,LAST_ERROR_REASON=NULL");
        migrator.migrate(legacy,"sa","",directory.resolve("backups"),Map.of(Game.WEISS_SCHWARZ,ws,Game.YUGIOH_OCG,ocg));
        assertThat(o.queryForObject("SELECT PENDING FROM QQ_NEWS_DELIVERIES",Boolean.class)).isFalse();
        assertThat(o.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> LegacyStorageFixture.news(w,303,"YUGIOH_OCG","c".repeat(64))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void refusesToMergeAnUnmarkedNonemptyDestination() throws Exception {
        String legacy=url("legacy");
        var source=LegacyStorageFixture.source(legacy); LegacyStorageFixture.schema(source); LegacyStorageFixture.history(source);
        var ws=destination("ws"); var ocg=destination("ocg");
        LegacyStorageFixture.news(new JdbcTemplate(ws),999,"WEISS_SCHWARZ","c".repeat(64));
        assertThatThrownBy(() -> new LegacyGameStorageMigrator().migrate(legacy,"sa","",directory.resolve("backup"),
                Map.of(Game.WEISS_SCHWARZ,ws,Game.YUGIOH_OCG,ocg))).hasMessageContaining("Refusing to merge");
        assertThat(new JdbcTemplate(ws).queryForObject("SELECT ID FROM NEWS_ITEMS",Long.class)).isEqualTo(999);
        assertThat(new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isEqualTo(2);
    }

    @Test
    void rejectsMismatchedDeliveryBeforeCopyingEitherGame() throws Exception {
        String legacy=url("legacy");
        var source=LegacyStorageFixture.source(legacy); LegacyStorageFixture.schema(source); LegacyStorageFixture.history(source);
        new JdbcTemplate(source).update("UPDATE QQ_NEWS_DELIVERIES SET SOURCE_ID='ws-jp-rsshub-card-reveals' WHERE NEWS_ID=202");
        var ws=destination("ws"); var ocg=destination("ocg");
        assertThatThrownBy(() -> new LegacyGameStorageMigrator().migrate(legacy,"sa","",directory.resolve("backup"),
                Map.of(Game.WEISS_SCHWARZ,ws,Game.YUGIOH_OCG,ocg))).hasMessageContaining("game mismatch");
        assertThat(new JdbcTemplate(ws).queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isZero();
        assertThat(new JdbcTemplate(ocg).queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isZero();
    }

    @Test
    void preventsDifferentUrlSpellingsFromOpeningTheSameDatabase() {
        assertThatThrownBy(() -> H2FileLocations.requireDifferent(url("ws"), url("folder/../ws")))
                .hasMessageContaining("must be different");
    }

    private String url(String name) { return "jdbc:h2:file:"+directory.resolve(name).toString().replace('\\','/'); }

    @Test
    void resumesAfterOneGameTransactionFailedWithoutRecopyingTheCommittedGame() throws Exception {
        String legacy=url("legacy");
        var source=LegacyStorageFixture.source(legacy); LegacyStorageFixture.schema(source); LegacyStorageFixture.history(source);
        var ws=destination("ws"); var ocg=destination("ocg");
        var w=new JdbcTemplate(ws); var o=new JdbcTemplate(ocg);
        o.execute("ALTER TABLE NEWS_ITEMS ADD CONSTRAINT SYNTHETIC_FAILURE CHECK (ID<1)");
        var migrator=new LegacyGameStorageMigrator();
        assertThatThrownBy(() -> migrator.migrate(legacy,"sa","",directory.resolve("backup"),
                Map.of(Game.WEISS_SCHWARZ,ws,Game.YUGIOH_OCG,ocg))).isInstanceOf(RuntimeException.class);
        assertThat(w.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isEqualTo(1);
        assertThat(o.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isZero();
        o.execute("ALTER TABLE NEWS_ITEMS DROP CONSTRAINT SYNTHETIC_FAILURE");
        migrator.migrate(legacy,"sa","",directory.resolve("backup"),Map.of(Game.WEISS_SCHWARZ,ws,Game.YUGIOH_OCG,ocg));
        assertThat(w.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isEqualTo(1);
        assertThat(o.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isEqualTo(1);
        assertThat(w.queryForObject("SELECT PENDING FROM QQ_NEWS_DELIVERIES",Boolean.class)).isFalse();
    }

    @Test
    void migratesAnEarlyWsOnlyNativeEnumWithoutEditingTheLegacyEnumOrInventingOptionalFields() throws Exception {
        String legacy=url("early");
        var source=LegacyStorageFixture.source(legacy); LegacyStorageFixture.schema(source);
        var old=new JdbcTemplate(source);
        old.execute("ALTER TABLE NEWS_ITEMS ALTER COLUMN GAME ENUM('WEISS_SCHWARZ') NOT NULL");
        LegacyStorageFixture.news(old,5,"WEISS_SCHWARZ","a".repeat(64));
        old.execute("ALTER TABLE NEWS_ITEMS DROP COLUMN PUBLISHED_AT");
        var ws=destination("ws"); var ocg=destination("ocg");
        new LegacyGameStorageMigrator().migrate(legacy,"sa","",directory.resolve("backup"),Map.of(Game.WEISS_SCHWARZ,ws,Game.YUGIOH_OCG,ocg));
        assertThat(new JdbcTemplate(ws).queryForObject("SELECT PUBLISHED_AT FROM NEWS_ITEMS",Object.class)).isNull();
        assertThat(new JdbcTemplate(ocg).queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS",Long.class)).isZero();
        assertThat(old.queryForList("SELECT VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES",String.class)).containsExactly("WEISS_SCHWARZ");
    }
    private DataSource destination(String name) { var source=LegacyStorageFixture.source(url(name)); LegacyStorageFixture.schema(source); return source; }
}

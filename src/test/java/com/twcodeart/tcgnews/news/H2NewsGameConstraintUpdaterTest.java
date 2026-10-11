package com.twcodeart.tcgnews.news;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
class H2NewsGameConstraintUpdaterTest {
    @Test void keepsOldRowsExpandsTheLegacyEnumConstraintAndIsIdempotent() {
        var jdbc = database("legacy-enum");
        jdbc.execute("CREATE TABLE NEWS_ITEMS(ID BIGINT, GAME VARCHAR(32) CHECK(GAME IN('WEISS_SCHWARZ')))");
        jdbc.update("INSERT INTO NEWS_ITEMS VALUES(1,'WEISS_SCHWARZ')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO NEWS_ITEMS VALUES(2,'YUGIOH_OCG')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var updater = new H2NewsGameConstraintUpdater(jdbc);
        updater.run(null);
        updater.run(null);
        jdbc.update("INSERT INTO NEWS_ITEMS VALUES(2,'YUGIOH_OCG')");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO NEWS_ITEMS VALUES(3,'UNKNOWN_GAME')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void leavesUnrelatedChecksIntact() {
        var jdbc = database("unrelated-checks");
        jdbc.execute("CREATE TABLE NEWS_ITEMS(ID BIGINT CHECK(ID>0), GAME VARCHAR(32) CHECK(GAME='WEISS_SCHWARZ'))");
        new H2NewsGameConstraintUpdater(jdbc).run(null);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO NEWS_ITEMS VALUES(-1,'YUGIOH_OCG')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO NEWS_ITEMS VALUES(1,'YUGIOH_OCG')");
    }

    @Test void populatedLegacyNativeEnumRejectsOcgThenUpgradesWithoutLosingRowsDefaultsOrOtherChecks() {
        var jdbc=database("legacy-native-enum");
        jdbc.execute("CREATE TABLE NEWS_ITEMS(ID BIGINT PRIMARY KEY CHECK(ID>0), GAME ENUM('WEISS_SCHWARZ') DEFAULT 'WEISS_SCHWARZ' NOT NULL)");
        jdbc.execute("CREATE TABLE SOURCE_NOTIFICATION_BOOTSTRAPS(SOURCE_ID VARCHAR PRIMARY KEY)");
        jdbc.update("INSERT INTO NEWS_ITEMS(ID) VALUES(1)");
        jdbc.update("INSERT INTO SOURCE_NOTIFICATION_BOOTSTRAPS VALUES('ws-history')");
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS WHERE GAME=?",Integer.class,"YUGIOH_OCG"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        var updater=new H2NewsGameConstraintUpdater(jdbc);updater.run(null);updater.run(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM NEWS_ITEMS WHERE GAME=?",Integer.class,"YUGIOH_OCG")).isZero();
        jdbc.update("INSERT INTO NEWS_ITEMS VALUES(2,'YUGIOH_OCG')");
        jdbc.update("INSERT INTO NEWS_ITEMS(ID) VALUES(3)");
        assertThat(jdbc.queryForObject("SELECT GAME FROM NEWS_ITEMS WHERE ID=1",String.class)).isEqualTo("WEISS_SCHWARZ");
        assertThat(jdbc.queryForObject("SELECT GAME FROM NEWS_ITEMS WHERE ID=3",String.class)).isEqualTo("WEISS_SCHWARZ");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SOURCE_NOTIFICATION_BOOTSTRAPS",Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO NEWS_ITEMS VALUES(-1,'YUGIOH_OCG')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO NEWS_ITEMS VALUES(4,NULL)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private JdbcTemplate database(String name) {
        return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }
}

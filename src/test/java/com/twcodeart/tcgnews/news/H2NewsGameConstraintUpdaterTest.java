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
    private JdbcTemplate database(String name) {
        return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }
}

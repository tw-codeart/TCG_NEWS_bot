package com.twcodeart.tcgnews.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Expands legacy H2 enum checks without deleting news or bootstrap history. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "storage", name = "separated-enabled",
        havingValue = "false", matchIfMissing = true)
public class H2NewsGameConstraintUpdater implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(H2NewsGameConstraintUpdater.class);
    private final JdbcTemplate jdbc;
    public H2NewsGameConstraintUpdater(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        expandNativeEnums();
        var checks = jdbc.queryForList("""
                SELECT T.CONSTRAINT_NAME, C.CHECK_CLAUSE, T.TABLE_SCHEMA
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS T
                JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS C
                  ON T.CONSTRAINT_SCHEMA=C.CONSTRAINT_SCHEMA AND T.CONSTRAINT_NAME=C.CONSTRAINT_NAME
                WHERE T.TABLE_NAME='NEWS_ITEMS' AND T.TABLE_SCHEMA=CURRENT_SCHEMA()
                  AND T.CONSTRAINT_TYPE='CHECK'
                """);
        String allowed = Arrays.stream(Game.values()).map(game -> "'" + game.name() + "'")
                .collect(Collectors.joining(","));
        for (var check : checks) {
            String clause = check.get("CHECK_CLAUSE").toString().replace("\"", "")
                    .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            boolean enumCheck = clause.matches("\\(*GAME='[A-Z_]+'\\)*")
                    || clause.matches("\\(*GAMEIN\\('[A-Z_]+'(?:,'[A-Z_]+')*\\)\\)*");
            if (!enumCheck || Arrays.stream(Game.values()).allMatch(game -> clause.contains("'" + game.name() + "'"))) {
                continue;
            }
            String table = quote(check.get("TABLE_SCHEMA").toString()) + ".\"NEWS_ITEMS\"";
            String oldName = check.get("CONSTRAINT_NAME").toString();
            // Add the broader valid check first, so there is never an unprotected gap.
            jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT IF NOT EXISTS " + quote(oldName + "_EXPANDED")
                    + " CHECK (\"GAME\" IN (" + allowed + "))");
            jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + quote(oldName));
            log.info("Expanded legacy H2 news game check; existing news rows retained");
        }
    }

    private void expandNativeEnums() {
        var columns=jdbc.queryForList("""
                SELECT TABLE_SCHEMA, DTD_IDENTIFIER, IS_NULLABLE, COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME='NEWS_ITEMS' AND TABLE_SCHEMA=CURRENT_SCHEMA()
                  AND COLUMN_NAME='GAME' AND DATA_TYPE='ENUM'
                """);
        for(var column:columns) {
            var labels=jdbc.queryForList("""
                    SELECT VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES
                    WHERE OBJECT_SCHEMA=? AND OBJECT_NAME='NEWS_ITEMS' AND OBJECT_TYPE='TABLE'
                      AND ENUM_IDENTIFIER=? ORDER BY VALUE_ORDINAL
                    """,String.class,column.get("TABLE_SCHEMA"),column.get("DTD_IDENTIFIER"));
            if(labels.containsAll(Arrays.stream(Game.values()).map(Game::name).toList())) continue;
            // Preserve existing values/order and append supported games; never recreate the table.
            var expanded=new java.util.LinkedHashSet<String>(labels);
            Arrays.stream(Game.values()).map(Game::name).forEach(expanded::add);
            String declaration=expanded.stream().map(value -> "'"+value.replace("'","''")+"'")
                    .collect(Collectors.joining(","));
            String attributes=column.get("COLUMN_DEFAULT")==null ? "" : " DEFAULT "+column.get("COLUMN_DEFAULT");
            if("NO".equals(column.get("IS_NULLABLE"))) attributes+=" NOT NULL";
            jdbc.execute("ALTER TABLE "+quote(column.get("TABLE_SCHEMA").toString())
                    +".\"NEWS_ITEMS\" ALTER COLUMN \"GAME\" ENUM("+declaration+")"+attributes);
            log.info("Expanded legacy H2 NEWS_ITEMS.GAME native enum; existing news rows retained");
        }
    }
    private String quote(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }
}

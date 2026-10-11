package com.twcodeart.tcgnews.storage;

import com.twcodeart.tcgnews.news.Game;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "storage", name = "separated-enabled", havingValue = "true")
public class GameStorageInitializer implements ApplicationRunner {
    private final DataSource ws, ocg;
    private final DataSourceProperties legacy;
    private final GameStorageProperties properties;
    private final GameStorage storage;
    public GameStorageInitializer(@Qualifier("dataSource") DataSource ws, @Qualifier("ocgDataSource") DataSource ocg,
                                   DataSourceProperties legacy, GameStorageProperties properties, GameStorage storage) {
        this.ws = ws; this.ocg = ocg; this.legacy = legacy; this.properties = properties; this.storage = storage;
    }
    public void run(ApplicationArguments args) throws Exception {
        new LegacyGameStorageMigrator().migrate(legacy.getUrl(), legacy.determineUsername(), legacy.determinePassword(),
                Path.of(properties.getBackupDirectory()), Map.of(Game.WEISS_SCHWARZ, ws, Game.YUGIOH_OCG, ocg));
        storage.initialized();
    }
}

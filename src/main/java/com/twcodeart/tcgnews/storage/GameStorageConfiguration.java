package com.twcodeart.tcgnews.storage;

import com.twcodeart.tcgnews.news.NewsItemRepository;
import com.twcodeart.tcgnews.notification.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "storage", name = "separated-enabled", havingValue = "true")
public class GameStorageConfiguration {
    @Bean(name = "dataSource") @Primary
    DataSource wsDataSource(GameStorageProperties settings, DataSourceProperties legacy) {
        validateLocations(settings, legacy);
        return dataSource(settings.getWsUrl(), legacy);
    }

    @Bean(name = "ocgDataSource")
    DataSource ocgDataSource(GameStorageProperties settings, DataSourceProperties legacy) {
        validateLocations(settings, legacy);
        return dataSource(settings.getOcgUrl(), legacy);
    }

    private void validateLocations(GameStorageProperties settings, DataSourceProperties legacy) {
        H2FileLocations.requireDifferent(settings.getWsUrl(), settings.getOcgUrl());
        H2FileLocations.requireDifferent(settings.getWsUrl(), legacy.getUrl());
        H2FileLocations.requireDifferent(settings.getOcgUrl(), legacy.getUrl());
    }

    private DataSource dataSource(String url, DataSourceProperties legacy) {
        if (url == null || !url.startsWith("jdbc:h2:")) throw new IllegalArgumentException("Game storage requires H2");
        var source = new HikariDataSource();
        source.setJdbcUrl(url); source.setUsername(legacy.determineUsername()); source.setPassword(legacy.determinePassword());
        source.setMaximumPoolSize(5);
        // Confirmed QQ stamps must not wait for H2's default delayed log flush.
        source.setConnectionInitSql("SET WRITE_DELAY 0");
        return source;
    }

    @Bean(name = "entityManagerFactory") @Primary
    LocalContainerEntityManagerFactoryBean wsEntityManagerFactory(EntityManagerFactoryBuilder builder,
            @Qualifier("dataSource") DataSource source, Environment environment) {
        return factory(builder, source, environment, "ws");
    }

    @Bean(name = "ocgEntityManagerFactory")
    LocalContainerEntityManagerFactoryBean ocgEntityManagerFactory(EntityManagerFactoryBuilder builder,
            @Qualifier("ocgDataSource") DataSource source, Environment environment) {
        return factory(builder, source, environment, "ocg");
    }

    private LocalContainerEntityManagerFactoryBean factory(EntityManagerFactoryBuilder builder, DataSource source,
                                                          Environment environment, String unit) {
        return builder.dataSource(source).packages("com.twcodeart.tcgnews").persistenceUnit(unit).properties(Map.of(
                "hibernate.hbm2ddl.auto", environment.getProperty("spring.jpa.hibernate.ddl-auto", "update"),
                "hibernate.jdbc.time_zone", "UTC")).build();
    }

    @Bean(name = "transactionManager") @Primary
    JpaTransactionManager wsTransactionManager(@Qualifier("entityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }

    @Bean(name = "ocgTransactionManager")
    JpaTransactionManager ocgTransactionManager(@Qualifier("ocgEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }

    @Bean
    OcgStore ocgStore(@Qualifier("ocgEntityManagerFactory") EntityManagerFactory factory,
                      @Qualifier("ocgTransactionManager") JpaTransactionManager transactions) {
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory));
        return new OcgStore(new GameRepositories(repositories.getRepository(NewsItemRepository.class),
                repositories.getRepository(SourceNotificationBootstrapRepository.class),
                repositories.getRepository(QqDeliveryCursorRepository.class),
                repositories.getRepository(QqNewsDeliveryRepository.class),
                repositories.getRepository(com.twcodeart.tcgnews.subscription.QqGroupSubscriptionRepository.class),
                repositories.getRepository(com.twcodeart.tcgnews.subscription.QqGroupDeliveryRepository.class),
                repositories.getRepository(com.twcodeart.tcgnews.subscription.ProcessedQqCommandRepository.class)), new TransactionTemplate(transactions));
    }
    public record OcgStore(GameRepositories repositories, TransactionTemplate transactions) { }
}

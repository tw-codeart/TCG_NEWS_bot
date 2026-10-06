package com.twcodeart.tcgnews.source.ws;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class RssHubConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RssHubProperties.class, WsRssHubFeedClient.class,
                    WsRssHubFeedParser.class, WsRssHubNewsSource.class);

    @Test
    void rejectsNotificationBatchLimitsOutsideOneToFive() {
        for (int limit : new int[] {0, 6, 100}) {
            context.withPropertyValues("rsshub.enabled=true", "rsshub.base-url=https://rsshub.example",
                    "rsshub.notification-batch-limit=" + limit).run(app -> {
                assertThat(app).hasFailed();
                assertThat(app.getStartupFailure()).hasRootCauseMessage(
                        "rsshub.notification-batch-limit must be between 1 and 5");
            });
        }
    }

    @Test
    void isDisabledByDefaultAndDoesNotRequireAnEndpoint() {
        context.run(app -> {
            assertThat(app).hasNotFailed().doesNotHaveBean(WsRssHubNewsSource.class)
                    .doesNotHaveBean(WsRssHubFeedClient.class).doesNotHaveBean(RssHubProperties.class);
        });
    }

    @Test
    void bindsConfiguredEndpointAndRegistersTheOptionalSource() {
        context.withPropertyValues("rsshub.enabled=true", "rsshub.base-url=https://rsshub.example/base/")
                .run(app -> {
                    assertThat(app).hasNotFailed().hasSingleBean(WsRssHubNewsSource.class);
                    assertThat(app.getBean(RssHubProperties.class).feedUri().toString())
                            .isEqualTo("https://rsshub.example/base/twitter/user/wstcg");
                });
    }

    @Test
    void failsClearlyWhenEnabledWithoutABaseUrl() {
        context.withPropertyValues("rsshub.enabled=true").run(app -> {
            assertThat(app).hasFailed();
            assertThat(app.getStartupFailure()).hasRootCauseMessage(
                    "rsshub.base-url is required when RSSHub is enabled");
        });
    }
}

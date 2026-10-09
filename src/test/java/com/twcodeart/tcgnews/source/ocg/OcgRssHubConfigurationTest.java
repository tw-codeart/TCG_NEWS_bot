package com.twcodeart.tcgnews.source.ocg;
import com.twcodeart.tcgnews.source.ws.RssHubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class OcgRssHubConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RssHubProperties.class, OcgRssHubFeedParser.class, OcgRssHubNewsSource.class);
    @Test void requiresExplicitOcgEnablementAndUsesTheSameRssHubBaseUrl() {
        context.withPropertyValues("rsshub.enabled=true", "rsshub.base-url=http://localhost:1200")
                .run(app -> assertThat(app).hasNotFailed().doesNotHaveBean(OcgRssHubNewsSource.class));
        context.withPropertyValues("rsshub.enabled=true", "rsshub.ocg-enabled=true", "rsshub.base-url=http://localhost:1200")
                .run(app -> {
                    assertThat(app).hasNotFailed().hasSingleBean(OcgRssHubNewsSource.class);
                    assertThat(app.getBean(RssHubProperties.class).ocgFeedUri().toString())
                            .isEqualTo("http://localhost:1200/twitter/user/YuGiOh_OCG_INFO");
                });
    }
}

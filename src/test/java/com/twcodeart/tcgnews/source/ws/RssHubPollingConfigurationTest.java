package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.NewsFetchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RssHubPollingConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RssHubProperties.class, RssHubPollingService.class)
            .withBean(NewsFetchService.class, () -> mock(NewsFetchService.class));

    @Test
    void isDisabledByDefaultAndRequiresTheRssHubSourceToBeEnabled() {
        context.withPropertyValues("rsshub.enabled=true").run(app ->
                assertThat(app).hasNotFailed().doesNotHaveBean(RssHubPollingService.class));
        context.withPropertyValues("rsshub.enabled=false", "rsshub.polling-enabled=true").run(app ->
                assertThat(app).hasNotFailed().doesNotHaveBean(RssHubPollingService.class));
    }

    @Test
    void registersOneFixedDelayTaskWithTheConfiguredIntervalAndInitialDelay() {
        context.withPropertyValues("rsshub.enabled=true", "rsshub.polling-enabled=true", "rsshub.polling-interval=PT15M")
                .run(app -> {
                    assertThat(app).hasNotFailed().hasSingleBean(RssHubPollingService.class);
                    var tasks = app.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
                    assertThat(tasks).hasSize(1);
                    var task = (FixedDelayTask) tasks.iterator().next().getTask();
                    assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void rejectsIntervalsThatWouldCauseExcessivePolling() {
        context.withPropertyValues("rsshub.enabled=true", "rsshub.polling-enabled=true", "rsshub.polling-interval=PT1S")
                .run(app -> {
                    assertThat(app).hasFailed();
                    assertThat(app.getStartupFailure()).hasRootCauseMessage("rsshub.polling-interval must be at least 1 minute");
                });
    }
}

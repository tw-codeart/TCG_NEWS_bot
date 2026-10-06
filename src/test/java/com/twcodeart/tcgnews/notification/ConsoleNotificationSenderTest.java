package com.twcodeart.tcgnews.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleNotificationSenderTest {

    @Test
    void logsTheSuppliedMultilineMessageWithoutReformatting() {
        String message = "[Weiß Schwarz News]\n\nTitle: Synthetic notice\n\nOfficial source:\n"
                + "https://ws-tcg.com/information/test/";
        Logger logger = (Logger) LoggerFactory.getLogger(ConsoleNotificationSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new ConsoleNotificationSender().send(message);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
            assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("Notification:\n" + message);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}

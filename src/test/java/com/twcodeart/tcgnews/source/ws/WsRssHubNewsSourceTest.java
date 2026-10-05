package com.twcodeart.tcgnews.source.ws;

import com.rometools.rome.io.FeedException;
import com.twcodeart.tcgnews.source.NewsSourceException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WsRssHubNewsSourceTest {
    private final WsRssHubFeedClient client = mock(WsRssHubFeedClient.class);
    private final WsRssHubNewsSource source = new WsRssHubNewsSource(client, new WsRssHubFeedParser());

    @Test
    void delegatesFeedParsingAndWrapsRetrievalFailuresWithTheirCause() throws Exception {
        when(client.fetch()).thenReturn(WsRssHubFeedParserTest.fixture("ws-rsshub.xml"));
        assertThat(source.fetchNews()).hasSize(6);
        IOException failure = new IOException("Endpoint unavailable");
        when(client.fetch()).thenThrow(failure);
        assertThatThrownBy(source::fetchNews).isInstanceOf(NewsSourceException.class).hasCause(failure);
    }

    @Test
    void reportsWholeFeedParsingFailures() throws Exception {
        when(client.fetch()).thenReturn(WsRssHubFeedParserTest.fixture("ws-rsshub-malformed.xml"));
        assertThatThrownBy(source::fetchNews).isInstanceOf(NewsSourceException.class)
                .hasCauseInstanceOf(FeedException.class);
    }

    @Test
    void preservesInterruptSignal() throws Exception {
        InterruptedException interrupted = new InterruptedException("Cancelled request");
        when(client.fetch()).thenThrow(interrupted);
        try {
            assertThatThrownBy(source::fetchNews).isInstanceOf(NewsSourceException.class).hasCause(interrupted);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}

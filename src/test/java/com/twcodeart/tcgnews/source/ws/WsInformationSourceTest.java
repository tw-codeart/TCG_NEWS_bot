package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.source.NewsSourceException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsInformationSourceTest {

    @Mock
    private WsInformationPageClient client;

    @Test
    void fetchesThroughTheHttpClientAndReturnsParsedInformation() throws Exception {
        try (InputStream fixture = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/ws-information.html"))) {
            when(client.fetch()).thenReturn(Jsoup.parse(fixture, "UTF-8", WsInformationPageClient.INFORMATION_URL));
        }
        var source = new WsInformationSource(client, new WsInformationParser());

        assertThat(source.fetchNews()).hasSize(6);
        verify(client).fetch();
    }

    @Test
    void preservesTheOriginalHttpFailureCause() throws IOException {
        IOException failure = new IOException("Connection timed out");
        when(client.fetch()).thenThrow(failure);
        var source = new WsInformationSource(client, new WsInformationParser());

        assertThatThrownBy(source::fetchNews).isInstanceOf(NewsSourceException.class)
                .hasCause(failure).hasMessageContaining(WsInformationPageClient.INFORMATION_URL);
    }
}

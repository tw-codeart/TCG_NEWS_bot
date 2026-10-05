package com.twcodeart.tcgnews.source.ws;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WsRssHubFeedClientTest {
    private final HttpClient http = mock(HttpClient.class);
    private final WsRssHubFeedClient client = new WsRssHubFeedClient(
            URI.create("https://rsshub.example/twitter/user/wstcg"), http);

    @Test
    void retrievesRawFeedWithoutXAuthentication() throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        byte[] bytes = WsRssHubFeedParserTest.fixture("ws-rsshub.xml");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(bytes);
        assertThat(client.fetch()).isEqualTo(bytes);
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().headers().firstValue("Authorization")).isEmpty();
        assertThat(request.getValue().headers().firstValue("Cookie")).isEmpty();
    }

    @Test
    void rejectsNon200ResponsesAndPreservesUnavailableEndpointErrors() throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(503);
        assertThatThrownBy(client::fetch).isInstanceOf(IOException.class).hasMessageContaining("HTTP 503");
        IOException unavailable = new IOException("Connection refused");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(unavailable);
        assertThatThrownBy(client::fetch).isSameAs(unavailable);
    }
}

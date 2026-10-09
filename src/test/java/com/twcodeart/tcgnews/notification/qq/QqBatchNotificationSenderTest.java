package com.twcodeart.tcgnews.notification.qq;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twcodeart.tcgnews.notification.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class QqBatchNotificationSenderTest {
    @Test void packsThirteenDistinctOriginalImagesIntoOneForwardWithoutTextLinks() {
        var props=new QqProperties(); props.setEnabled(true); props.setTestGroupId("42");
        props.setApiBaseUrl("http://qq-api.example"); props.setOcrEnabled(false);
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var sender=new QqNotificationSender(props,builder.build());
        server.expect(requestTo("http://qq-api.example/get_login_info")).andRespond(withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"user_id\":123,\"nickname\":\"Test Bot\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qq-api.example/send_group_forward_msg")).andExpect(request -> {
            var json=new ObjectMapper().readTree(((MockClientHttpRequest)request).getBodyAsBytes());
            assertThat(json.path("group_id").asLong()).isEqualTo(42);
            assertThat(json.path("messages").size()).isEqualTo(26);
            var images=new java.util.ArrayList<String>();
            for(var node:json.path("messages")) for(var segment:node.path("data").path("content")) {
                if("image".equals(segment.path("type").asText())) images.add(segment.path("data").path("file").asText());
                else assertThat(segment.path("data").path("text").asText()).doesNotContain("https://", "yugioh-card.com");
            }
            assertThat(images).hasSize(13).doesNotHaveDuplicates();
        }).andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}",MediaType.APPLICATION_JSON));
        sender.sendBatch(messages(13)); server.verify();
    }
    @Test void emptyAndOversizedPacketsOrInvalidImagesNeverMakeHttpRequests() {
        var props=new QqProperties(); props.setEnabled(true); props.setTestGroupId("42");
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var sender=new QqNotificationSender(props,builder.build());
        sender.sendBatch(List.of());
        assertThatThrownBy(() -> sender.sendBatch(messages(21))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sender.sendBatch(List.of(new NotificationMessage("test","http://127.0.0.1/image"))))
                .isInstanceOf(QqNotificationException.class);
        server.verify();
    }
    private List<NotificationMessage> messages(int count) {
        return IntStream.range(0,count).mapToObj(n -> new NotificationMessage("収録カード公開 https://x.com/YuGiOh_OCG_INFO/status/1 yugioh-card.com/japan/products…",
                "https://pbs.twimg.com/media/synthetic-"+n+".png")).toList();
    }
}

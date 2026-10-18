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
    @Test void sendsExtractedArticleTextOnceWithAllThreeOriginalCardImages() {
        var props=new QqProperties();props.setEnabled(true);props.setTestGroupId("42");
        props.setApiBaseUrl("http://qq-api.example");props.setOcrEnabled(false);
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        var sender=new QqNotificationSender(props,builder.build());
        var formatter=new com.twcodeart.tcgnews.notification.PlainTextNotificationFormatter();
        var messages=IntStream.range(0,3).mapToObj(index -> {
            var item=new com.twcodeart.tcgnews.news.NewsItem(com.twcodeart.tcgnews.news.Game.YUGIOH_OCG,
                    com.twcodeart.tcgnews.news.NewsCategory.CARD_REVEAL,"CARDニュース(OCG) Vジャンプ掲載記事",null,null,
                    "https://yu-gi-oh.jp/media/cms/v1/card-"+index+".webp","https://x.com/yu_gi_oh_jp/status/101","a".repeat(64));
            item.setArticleText("新規カードの公式説明\n第二行");item.setArticleUrl("https://yu-gi-oh.jp/news/article/");
            return new NotificationMessage(formatter.format(item),item.getImageUrl());
        }).toList();
        server.expect(requestTo("http://qq-api.example/get_login_info")).andRespond(withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"user_id\":123}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qq-api.example/send_group_forward_msg")).andExpect(request -> {
            var json=new ObjectMapper().readTree(((MockClientHttpRequest)request).getBodyAsBytes());
            assertThat(json.path("messages").size()).isEqualTo(4);
            assertThat(json.toString().split("新規カードの公式説明",-1)).hasSize(2);
            for(int index=0;index<3;index++)assertThat(json.toString()).contains("https://yu-gi-oh.jp/media/cms/v1/card-"+index+".webp");
            var text=json.path("messages").get(0).path("data").path("content").get(0).path("data").path("text").asText();
            assertThat(text).contains("第二行").doesNotContain("https://");
        }).andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}",MediaType.APPLICATION_JSON));
        sender.sendBatch(messages);server.verify();
    }
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
            assertThat(json.path("messages").size()).isEqualTo(14);
            assertThat(json.path("messages").toString().split("収録カード公開",-1)).hasSize(2);
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
        assertThatThrownBy(() -> sender.sendBatch(messages(51))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sender.sendBatch(List.of(new NotificationMessage("test","http://127.0.0.1/image"))))
                .isInstanceOf(QqNotificationException.class);
        server.verify();
    }
    private List<NotificationMessage> messages(int count) {
        return IntStream.range(0,count).mapToObj(n -> new NotificationMessage("収録カード公開 https://x.com/YuGiOh_OCG_INFO/status/1 yugioh-card.com/japan/products…",
                "https://pbs.twimg.com/media/synthetic-"+n+".png")).toList();
    }

    @Test void identicalVisibleBodiesAppearOnceEvenWhenSourceLinksDifferButDifferentBodiesRemain() {
        var props=new QqProperties();props.setEnabled(true);props.setTestGroupId("42");
        props.setApiBaseUrl("http://qq-api.example");props.setOcrEnabled(false);
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        var sender=new QqNotificationSender(props,builder.build());
        server.expect(requestTo("http://qq-api.example/get_login_info")).andRespond(withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"user_id\":123}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qq-api.example/send_group_forward_msg")).andExpect(request -> {
            var json=new ObjectMapper().readTree(((MockClientHttpRequest)request).getBodyAsBytes());
            assertThat(json.path("messages").size()).isEqualTo(5); // two body nodes, three unchanged image nodes
            assertThat(json.toString().split("Same post body",-1)).hasSize(2);
            assertThat(json.toString()).contains("Different post body","synthetic-1.png","synthetic-2.png","synthetic-3.png");
        }).andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}",MediaType.APPLICATION_JSON));
        sender.sendBatch(List.of(
                new NotificationMessage("Same post body https://x.com/wstcg/status/1","https://pbs.twimg.com/media/synthetic-1.png"),
                new NotificationMessage("Same post body https://x.com/wstcg/status/2","https://pbs.twimg.com/media/synthetic-2.png"),
                new NotificationMessage("Different post body","https://pbs.twimg.com/media/synthetic-3.png")));
        server.verify();
    }

    @Test void allowsFiftyImagesInOneRequestWithOneRepeatedBody() {
        var props=new QqProperties();props.setEnabled(true);props.setTestGroupId("42");
        props.setApiBaseUrl("http://qq-api.example");props.setOcrEnabled(false);
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        var sender=new QqNotificationSender(props,builder.build());
        server.expect(requestTo("http://qq-api.example/get_login_info")).andRespond(withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"user_id\":123}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qq-api.example/send_group_forward_msg")).andExpect(request -> {
            var json=new ObjectMapper().readTree(((MockClientHttpRequest)request).getBodyAsBytes());
            assertThat(json.path("messages").size()).isEqualTo(51);
        }).andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}",MediaType.APPLICATION_JSON));
        sender.sendBatch(messages(50));server.verify();
    }
}

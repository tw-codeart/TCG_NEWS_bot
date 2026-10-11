package com.twcodeart.tcgnews.notification.qq;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class QqGroupTransportTest {
    @Test void queriesTheActualMemberRoleAndBindsOnlyTheSubscribedDestination(){
        var props=new QqProperties();props.setEnabled(true);props.setTestGroupId("42");props.setApiBaseUrl("http://qq.example");
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var sender=new QqNotificationSender(props,builder.build());
        server.expect(requestTo("http://qq.example/get_group_member_info"))
                .andExpect(content().json("{\"group_id\":99,\"user_id\":7,\"no_cache\":true}"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"group_id\":99,\"user_id\":7,\"role\":\"admin\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qq.example/send_group_msg"))
                .andExpect(content().json("{\"group_id\":99}"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}",MediaType.APPLICATION_JSON));
        assertThat(sender.memberRole(99,7)).isEqualTo("admin");
        sender.forSubscribedGroup(99).send("Subscribed");assertThat(props.groupId()).isEqualTo(42);server.verify();
    }
    @Test void mismatchedMemberIdentityCannotGrantAdministrativeRole(){
        var props=new QqProperties();props.setEnabled(true);props.setTestGroupId("42");props.setApiBaseUrl("http://qq.example");
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var sender=new QqNotificationSender(props,builder.build());
        server.expect(requestTo("http://qq.example/get_group_member_info"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"group_id\":99,\"user_id\":8,\"role\":\"owner\"}}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->sender.memberRole(99,7)).isInstanceOf(QqNotificationException.class);server.verify();
    }
}

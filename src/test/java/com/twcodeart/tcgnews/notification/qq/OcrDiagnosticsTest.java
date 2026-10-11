package com.twcodeart.tcgnews.notification.qq;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OcrDiagnosticsTest {
    @Test void failedLocalOcrStillForwardsOriginalImageWithoutCallingNapcatOcr() {
        var properties=new QqProperties();properties.setEnabled(true);properties.setTestGroupId("42");
        properties.setApiBaseUrl("http://qq.example");properties.setOcrProvider("tesseract");
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://qq.example/get_login_info")).andRespond(withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"user_id\":123,\"nickname\":\"Bot\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://qq.example/send_group_forward_msg")).andExpect(content().string(org.hamcrest.Matchers.containsString("https://pbs.twimg.com/media/synthetic.jpg")))
                .andRespond(withSuccess("{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}",MediaType.APPLICATION_JSON));
        var sender=new QqNotificationSender(properties,builder.build());
        // Bound group must retain the selected provider and original image fallback.
        sender.forSubscribedGroup(42).send("synthetic", "https://pbs.twimg.com/media/synthetic.jpg");server.verify();
    }
    @Test void exposesOnlySafeProcessLocalCountsAndReasons() {
        var state=new OcrDiagnostics();state.record("napcat","EMPTY");state.record("tesseract","SUCCESS");state.record("napcat","TIMEOUT");
        assertThat(state.snapshot().successes()).isEqualTo(1);assertThat(state.snapshot().failures()).isEqualTo(2);
        assertThat(state.snapshot().result()).isEqualTo("TIMEOUT");assertThat(state.snapshot().lastAttemptAt()).isNotNull();
        assertThatThrownBy(()->new QqProperties().setOcrProvider("unknown")).isInstanceOf(IllegalArgumentException.class);
    }
}

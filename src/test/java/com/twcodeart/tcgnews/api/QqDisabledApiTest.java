package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.notification.qq.QqNotificationSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qq-disabled-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "rsshub.enabled=false", "qq.enabled=false"
})
@AutoConfigureMockMvc
class QqDisabledApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    @Test
    void disabledQqHasNoSenderBeanAndBothEndpointsReturn503WithoutHttpAccess() throws Exception {
        assertThat(context.getBeansOfType(QqNotificationSender.class)).isEmpty();
        for (String endpoint : new String[] {"/api/notifications/test", "/api/notifications/news"}) {
            mvc.perform(post(endpoint)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("QQ notifications are disabled"));
        }
    }
}

package com.twcodeart.tcgnews.operations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bot.retention")
public class RetentionProperties {
    private boolean enabled;
    private int commandDays = 30;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getCommandDays() { return commandDays; }
    public void setCommandDays(int days) {
        if (days < 1 || days > 3650) throw new IllegalArgumentException("Command retention must be 1-3650 days");
        commandDays = days;
    }
}

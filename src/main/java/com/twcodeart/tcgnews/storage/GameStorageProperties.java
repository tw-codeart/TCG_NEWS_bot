package com.twcodeart.tcgnews.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "storage")
public class GameStorageProperties {
    private boolean separatedEnabled;
    private String wsUrl = "jdbc:h2:file:./data/ws-news";
    private String ocgUrl = "jdbc:h2:file:./data/ocg-news";
    private String backupDirectory = "./data/backups";
    public boolean isSeparatedEnabled() { return separatedEnabled; }
    public void setSeparatedEnabled(boolean value) { separatedEnabled = value; }
    public String getWsUrl() { return wsUrl; }
    public void setWsUrl(String value) { wsUrl = value; }
    public String getOcgUrl() { return ocgUrl; }
    public void setOcgUrl(String value) { ocgUrl = value; }
    public String getBackupDirectory() { return backupDirectory; }
    public void setBackupDirectory(String value) { backupDirectory = value; }
}

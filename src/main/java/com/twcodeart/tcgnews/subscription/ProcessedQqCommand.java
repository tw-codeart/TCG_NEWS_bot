package com.twcodeart.tcgnews.subscription;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="processed_qq_commands", indexes=@Index(name="idx_command_processed",columnList="processed_at"))
public class ProcessedQqCommand {
    @Id @Column(length=64) private String eventKey;
    @Column(nullable=false,updatable=false) private Instant processedAt;
    @Column(updatable=false) private Long boundGroupId;
    protected ProcessedQqCommand(){ }
    public ProcessedQqCommand(String eventKey){this.eventKey=eventKey;processedAt=Instant.now();}
    public ProcessedQqCommand(String eventKey,long groupId){this(eventKey);boundGroupId=groupId;}
    public Long getBoundGroupId(){return boundGroupId;}
}

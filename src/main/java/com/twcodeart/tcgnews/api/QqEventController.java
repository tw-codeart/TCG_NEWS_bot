package com.twcodeart.tcgnews.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twcodeart.tcgnews.diagnostics.DiagnosticIds;
import com.twcodeart.tcgnews.notification.qq.QqProperties;
import com.twcodeart.tcgnews.subscription.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix="qq",name="group-subscriptions-enabled",havingValue="true")
public class QqEventController {
    private final QqProperties properties;
    private final QqGroupCommandService commands;
    private final ObjectMapper mapper;
    public QqEventController(QqProperties properties,QqGroupCommandService commands,ObjectMapper mapper){this.properties=properties;this.commands=commands;this.mapper=mapper;}
    @PostMapping(value="/api/qq/events",consumes="application/json")
    public ResponseEntity<?> event(HttpServletRequest request,@RequestHeader(value="X-Signature",required=false)String signature) throws java.io.IOException {
        byte[] body=request.getInputStream().readNBytes(65537);
        if(body.length>65536)return error(413,"QQ event exceeds 64 KiB");
        if(!authentic(body,signature))return error(401,"Invalid QQ event signature");
        JsonNode event;
        try{event=mapper.readTree(body);}catch(com.fasterxml.jackson.core.JsonProcessingException exception){return error(400,"Malformed QQ event JSON");}
        if(event==null||!event.path("post_type").asText().equals("message")||!event.path("message_type").asText().equals("group")
                ||event.hasNonNull("anonymous"))return ResponseEntity.noContent().build();
        long self=positive(event.path("self_id")),group=positive(event.path("group_id")),user=positive(event.path("user_id"));
        if(self==0||group==0||user==0||self==user||!event.path("message_id").isIntegralNumber()
                ||!event.path("message_id").canConvertToLong())return ResponseEntity.noContent().build();
        if(!event.path("time").isIntegralNumber()||!event.path("time").canConvertToLong()
                ||Math.abs((double)Instant.now().getEpochSecond()-event.path("time").asLong())>300)return ResponseEntity.noContent().build();
        NewsGroupCommand command=NewsGroupCommand.parse(text(event.path("message"),self));
        if(command==null)return ResponseEntity.noContent().build();
        String key=QqSubscriptionStore.hash(self+"|"+group+"|"+user+"|"+event.path("message_id").asLong()+"|"+event.path("time").asLong());
        commands.handle(self,group,user,key,command);
        return ResponseEntity.noContent().build();
    }
    private boolean authentic(byte[] body,String signature){
        if(signature==null||!signature.matches("sha1=[0-9a-fA-F]{40}"))return false;
        try{var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(properties.getCommandSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA1"));
            return MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature.substring(5)));}
        catch(java.security.GeneralSecurityException exception){throw new IllegalStateException("Cannot verify QQ signature",exception);}
    }
    private String text(JsonNode message,long self){
        if(message.isTextual())return message.asText().contains("[CQ:")?null:message.asText();
        if(!message.isArray())return null;
        var text=new StringBuilder();
        for(var segment:message){
            if(segment.path("type").asText().equals("text"))text.append(segment.path("data").path("text").asText());
            else if(!segment.path("type").asText().equals("at")||positive(segment.path("data").path("qq"))!=self)return null;
            if(text.length()>200)return null;
        }
        return text.toString();
    }
    private long positive(JsonNode value){try{String text=value.asText();if(text.matches("[1-9][0-9]{0,18}"))return Long.parseLong(text);}
        catch(NumberFormatException exception){return 0;}return 0;}
    private ResponseEntity<?> error(int status,String message){return ResponseEntity.status(status).body(Map.of("message",message,"errorId",DiagnosticIds.current()));}
}

package com.twcodeart.tcgnews.operations;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.QqNewsDelivery;
import com.twcodeart.tcgnews.storage.GameStorage;
import com.twcodeart.tcgnews.subscription.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"storage.separated-enabled=true","spring.jpa.hibernate.ddl-auto=update","bot.retention.enabled=true"})
class CommandRetentionTest {
    static final Path ROOT=directory();
    static Path directory() { try{return Files.createTempDirectory("retention-tests-");}catch(Exception e){throw new IllegalStateException(e);} }
    static String url(String name){return "jdbc:h2:file:"+ROOT.resolve(name).toString().replace('\\','/');}
    @DynamicPropertySource static void config(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->url("legacy"));p.add("storage.ws-url",()->url("ws"));p.add("storage.ocg-url",()->url("ocg"));
        p.add("storage.backup-directory",()->ROOT.resolve("backups").toString());
    }
    @Autowired GameStorage storage;
    @Autowired CommandRetentionService service;
    @Autowired RetentionProperties properties;
    @BeforeEach void reset(){
        properties.setEnabled(true);properties.setCommandDays(30);
        for(var game:Game.values())storage.execute(game,r -> {r.commands().deleteAll();r.deliveries().deleteAll();r.news().deleteAll();return null;});
    }
    @Test void cleansOldReplayMarkersInBothGamesButPreservesBindingsNewsAndStamps(){
        Instant now=Instant.now();
        for(var game:Game.values())storage.execute(game,r -> {
            var old=new ProcessedQqCommand("old");ReflectionTestUtils.setField(old,"processedAt",now.minus(31,ChronoUnit.DAYS));
            var binding=new ProcessedQqCommand("binding",42);ReflectionTestUtils.setField(binding,"processedAt",now.minus(100,ChronoUnit.DAYS));
            r.commands().save(old);r.commands().save(binding);r.commands().save(new ProcessedQqCommand("recent"));
            var item=r.news().save(new NewsItem(game,NewsCategory.CARD_REVEAL,"test",null,null,null,"https://x.com/example/status/1",game.name()));
            var sent=new QqNewsDelivery(item.getId(),"synthetic");sent.attempted(true,null,null);r.deliveries().save(sent);
            return null;
        });
        service.cleanAt(now);
        assertThat(service.snapshot().deletedCommands()).isEqualTo(2);
        for(var game:Game.values())storage.execute(game,r -> {
            assertThat(r.commands().findAll()).hasSize(2);
            assertThat(r.commands().existsById("binding")).isTrue();assertThat(r.commands().existsById("recent")).isTrue();
            assertThat(r.news().count()).isEqualTo(1);assertThat(r.deliveries().findAll().get(0).isPending()).isFalse();return null;
        });
    }
    @Test void disabledCleanupLeavesOldMarkersUntouched(){
        properties.setEnabled(false);
        storage.execute(Game.WEISS_SCHWARZ,r->{var old=new ProcessedQqCommand("old");ReflectionTestUtils.setField(old,"processedAt",Instant.EPOCH);r.commands().save(old);return null;});
        service.cleanAt(Instant.now());
        boolean exists=storage.execute(Game.WEISS_SCHWARZ,r->r.commands().existsById("old"));
        assertThat(exists).isTrue();
    }
    @Test void validatesRetentionOutsideTheCallbackReplayWindow(){
        assertThatThrownBy(()->properties.setCommandDays(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->properties.setCommandDays(3651)).isInstanceOf(IllegalArgumentException.class);
    }
}

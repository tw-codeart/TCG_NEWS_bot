package com.twcodeart.tcgnews.operations;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.storage.*;
import com.twcodeart.tcgnews.subscription.ProcessedQqCommandRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RetentionFailureIsolationTest {
    @Test void failedWsCleanupDoesNotBlockOcgOrTouchDeliveryRepositories() {
        var store=mock(GameStorage.class);when(store.isReady()).thenReturn(true);when(store.isSeparated()).thenReturn(true);
        var commands=mock(ProcessedQqCommandRepository.class);when(commands.deleteExpired(any())).thenReturn(2);
        var repositories=new GameRepositories(null,null,null,null,null,null,commands);
        doAnswer(call -> {
            if(call.getArgument(0)==Game.WEISS_SCHWARZ)throw new IllegalStateException("Synthetic WS failure");
            Function<GameRepositories,Integer> work=call.getArgument(1);return work.apply(repositories);
        }).when(store).execute(any(Game.class),any());
        var settings=new RetentionProperties();settings.setEnabled(true);
        var service=new CommandRetentionService(store,settings);service.cleanAt(Instant.now());
        assertThat(service.snapshot().deletedCommands()).isEqualTo(2);assertThat(service.snapshot().failedGames()).isEqualTo(1);
        verify(commands).deleteExpired(any());
    }
}

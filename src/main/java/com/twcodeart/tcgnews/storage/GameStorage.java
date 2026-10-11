package com.twcodeart.tcgnews.storage;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItemRepository;
import com.twcodeart.tcgnews.notification.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Function;

/** Selects a persistence unit before starting a transaction; no HTTP work runs here. */
@Service
public class GameStorage {
    private final GameRepositories ws;
    private final TransactionTemplate transactions;
    private final GameStorageConfiguration.OcgStore ocg;
    private volatile boolean ready;

    public GameStorage(NewsItemRepository news, SourceNotificationBootstrapRepository bootstraps,
                        QqDeliveryCursorRepository cursors, QqNewsDeliveryRepository deliveries,
                        com.twcodeart.tcgnews.subscription.QqGroupSubscriptionRepository subscriptions,
                        com.twcodeart.tcgnews.subscription.QqGroupDeliveryRepository groupDeliveries,
                        com.twcodeart.tcgnews.subscription.ProcessedQqCommandRepository commands,
                        @Qualifier("transactionManager") PlatformTransactionManager transactions,
                        ObjectProvider<GameStorageConfiguration.OcgStore> ocg) {
        this.ws = new GameRepositories(news, bootstraps, cursors, deliveries,subscriptions,groupDeliveries,commands);
        this.transactions = new TransactionTemplate(transactions);
        this.ocg = ocg.getIfAvailable(); this.ready = this.ocg == null;
    }

    public boolean isSeparated() { return ocg != null; }
    public boolean isReady() { return ready; }
    public void initialized() { ready = true; }
    public void requireReady() {
        if (!ready) throw new IllegalStateException("Game database migration is not complete");
    }

    public <T> T execute(Game game, Function<GameRepositories, T> work) {
        requireReady();
        if (game == Game.YUGIOH_OCG && ocg != null) {
            return ocg.transactions().execute(status -> work.apply(ocg.repositories()));
        }
        return transactions.execute(status -> work.apply(ws));
    }
}

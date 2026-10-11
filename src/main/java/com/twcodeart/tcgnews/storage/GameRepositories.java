package com.twcodeart.tcgnews.storage;

import com.twcodeart.tcgnews.news.NewsItemRepository;
import com.twcodeart.tcgnews.notification.QqDeliveryCursorRepository;
import com.twcodeart.tcgnews.notification.QqNewsDeliveryRepository;
import com.twcodeart.tcgnews.notification.SourceNotificationBootstrapRepository;

public record GameRepositories(NewsItemRepository news, SourceNotificationBootstrapRepository bootstraps,
                               QqDeliveryCursorRepository cursors, QqNewsDeliveryRepository deliveries,
                               com.twcodeart.tcgnews.subscription.QqGroupSubscriptionRepository subscriptions,
                               com.twcodeart.tcgnews.subscription.QqGroupDeliveryRepository groupDeliveries,
                               com.twcodeart.tcgnews.subscription.ProcessedQqCommandRepository commands) { }

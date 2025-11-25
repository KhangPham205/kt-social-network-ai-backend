package com.kt.social.infra.neo4j.graph.listener;

import com.kt.social.domain.friendship.event.FriendshipAcceptedEvent;
import com.kt.social.domain.friendship.event.FriendshipDeletedEvent;
import com.kt.social.domain.user.event.UserCreatedEvent;
import com.kt.social.infra.neo4j.graph.service.GraphSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GraphEventListener {

    private final GraphSyncService graphSyncService;

    @EventListener
    @Async
    public void handleFriendshipAccepted(FriendshipAcceptedEvent event) {
        graphSyncService.createFriendship(event.senderId(), event.receiverId());
    }

    @EventListener
    @Async
    public void handleUserCreated(UserCreatedEvent event) {
        graphSyncService.syncUser(event.user());
    }

    @EventListener
    @Async
    public void handleFriendshipDeleted(FriendshipDeletedEvent event) {
        graphSyncService.removeFriendship(event.user1Id(), event.user2Id());
    }
}

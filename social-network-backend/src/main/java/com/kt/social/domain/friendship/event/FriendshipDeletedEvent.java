package com.kt.social.domain.friendship.event;

public record FriendshipDeletedEvent(Long user1Id, Long user2Id) {
}

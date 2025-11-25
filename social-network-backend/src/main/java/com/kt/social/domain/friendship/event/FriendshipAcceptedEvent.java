package com.kt.social.domain.friendship.event;

public record FriendshipAcceptedEvent(Long senderId, Long receiverId) {
}

package com.kt.social.domain.friendship.service;

import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.user.dto.UserProfileDto;

import java.util.List;

public interface FriendshipService {
    FriendshipResponse sendRequest(Long userId, Long targetId);
    FriendshipResponse acceptRequest(Long userId, Long requesterId);
    FriendshipResponse rejectRequest(Long userId, Long requesterId);
    FriendshipResponse unfriend(Long userId, Long friendId);
    List<UserProfileDto> getFriends(Long userId);
}

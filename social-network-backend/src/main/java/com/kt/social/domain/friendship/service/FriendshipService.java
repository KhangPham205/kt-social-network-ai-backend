package com.kt.social.domain.friendship.service;

import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.user.dto.UserProfileDto;

import java.util.List;

public interface FriendshipService {
    FriendshipResponse sendRequest(Long userId, Long targetId);
    FriendshipResponse acceptRequest(Long senderId, Long receiverId);
    FriendshipResponse rejectRequest(Long senderId, Long receiverId);
    FriendshipResponse unfriend(Long userId, Long friendId);

    FriendshipResponse blockUser(Long userId, Long targetId);
    FriendshipResponse unblockUser(Long userId, Long targetId);

    List<UserProfileDto> getPendingRequests(Long userId);
    List<UserProfileDto> getBlockedUsers(Long userId);

    FriendshipResponse unsendRequest(Long userId, Long targetId);
    List<UserProfileDto> getSentRequests(Long userId);

    List<UserProfileDto> getFriends(Long userId);
}
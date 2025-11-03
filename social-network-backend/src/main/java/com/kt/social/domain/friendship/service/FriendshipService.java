package com.kt.social.domain.friendship.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.user.dto.UserProfileDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FriendshipService {
    FriendshipResponse sendRequest(Long userId, Long targetId);
    FriendshipResponse acceptRequest(Long senderId, Long receiverId);
    FriendshipResponse rejectRequest(Long senderId, Long receiverId);
    FriendshipResponse unfriend(Long userId, Long friendId);

    FriendshipResponse blockUser(Long userId, Long targetId);
    FriendshipResponse unblockUser(Long userId, Long targetId);

    FriendshipResponse unsendRequest(Long userId, Long targetId);

    PageVO<UserProfileDto> getFriends(Long userId, Pageable pageable);
    PageVO<UserProfileDto> getPendingRequests(Long userId, Pageable pageable);
    PageVO<UserProfileDto> getSentRequests(Long userId, Pageable pageable);
    PageVO<UserProfileDto> getBlockedUsers(Long userId, Pageable pageable);
}
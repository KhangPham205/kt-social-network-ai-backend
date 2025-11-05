package com.kt.social.domain.friendship.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.model.Friendship;
import com.kt.social.domain.user.dto.UserProfileDto;
import com.kt.social.domain.user.dto.UserRelationDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface FriendshipService {
    FriendshipResponse sendRequest(Long userId, Long targetId);
    FriendshipResponse acceptRequest(Long senderId, Long receiverId);
    FriendshipResponse rejectRequest(Long senderId, Long receiverId);
    FriendshipResponse unfriend(Long userId, Long friendId);

    FriendshipResponse blockUser(Long userId, Long targetId);
    FriendshipResponse unblockUser(Long userId, Long targetId);

    FriendshipResponse unsendRequest(Long userId, Long targetId);

    PageVO<UserRelationDto> getFriends(Long userId, String filter, Pageable pageable);
    PageVO<UserRelationDto> getPendingRequests(Long userId, String filter, Pageable pageable);
    PageVO<UserRelationDto> getSentRequests(Long userId, String filter, Pageable pageable);
    PageVO<UserRelationDto> getBlockedUsers(Long userId, String filter, Pageable pageable);
}
package com.kt.social.domain.friendship.service.impl;

import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.model.Friendship;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.friendship.service.FriendshipService;
import com.kt.social.domain.user.dto.UserProfileDto;
import com.kt.social.domain.user.mapper.UserMapper;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public FriendshipResponse sendRequest(Long userId, Long targetId) {
        if (userId == null || targetId == null) {
            throw new IllegalArgumentException("userId and targetId must not be null");
        }

        if (userId.equals(targetId)) {
            throw new IllegalArgumentException("You cannot send a friend request to yourself");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User friend = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        if (friendshipRepository.findByUserAndFriend(user, friend).isPresent()) {
            throw new RuntimeException("Friend request already exists");
        }

        Friendship friendship = Friendship.builder()
                .user(user)
                .friend(friend)
                .status(FriendshipStatus.PENDING)
                .build();

        friendshipRepository.save(friendship);
        return new FriendshipResponse("Friend request sent", FriendshipStatus.PENDING);
    }

    @Override
    @Transactional
    public FriendshipResponse acceptRequest(Long userId, Long requesterId) {
        if (userId == null || requesterId == null) {
            throw new IllegalArgumentException("userId and requesterId must not be null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("Requester not found"));

        Friendship friendship = friendshipRepository.findByUserAndFriend(requester, user)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);

        // tạo quan hệ ngược để đảm bảo hai chiều
        if (friendshipRepository.findByUserAndFriend(user, requester).isEmpty()) {
            friendshipRepository.save(Friendship.builder()
                    .user(user)
                    .friend(requester)
                    .status(FriendshipStatus.ACCEPTED)
                    .build());
        }

        return new FriendshipResponse("Friend request accepted", FriendshipStatus.ACCEPTED);
    }

    @Override
    @Transactional
    public FriendshipResponse rejectRequest(Long userId, Long requesterId) {
        User user = userRepository.findById(userId).orElseThrow();
        User requester = userRepository.findById(requesterId).orElseThrow();

        Friendship friendship = friendshipRepository.findByUserAndFriend(requester, user)
                .orElseThrow(() -> new RuntimeException("No friend request found"));

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
        return new FriendshipResponse("Friend request rejected", FriendshipStatus.REJECTED);
    }

    @Override
    @Transactional
    public FriendshipResponse unfriend(Long userId, Long friendId) {
        User user = userRepository.findById(userId).orElseThrow();
        User friend = userRepository.findById(friendId).orElseThrow();

        friendshipRepository.findByUserAndFriend(user, friend)
                .ifPresent(friendshipRepository::delete);
        friendshipRepository.findByUserAndFriend(friend, user)
                .ifPresent(friendshipRepository::delete);

        return new FriendshipResponse("Unfriended successfully", FriendshipStatus.REJECTED);
    }

    @Override
    public List<UserProfileDto> getFriends(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Friendship> friendships = friendshipRepository.findByUserAndStatus(user, FriendshipStatus.ACCEPTED);
        return friendships.stream()
                .map(Friendship::getFriend)
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }
}

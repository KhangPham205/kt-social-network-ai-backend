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
import java.util.Optional;
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
        if (userId.equals(targetId)) {
            throw new IllegalArgumentException("You cannot send a friend request to yourself");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        User friend = userRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Target user not found"));

        Optional<Friendship> existing = friendshipRepository.findByUserAndFriend(user, friend)
                .or(() -> friendshipRepository.findByUserAndFriend(friend, user));

        if (existing.isPresent()) {
            Friendship f = existing.get();
            switch (f.getStatus()) {
                case BLOCKED -> throw new RuntimeException("You cannot send a request to a blocked user");
                case PENDING -> throw new RuntimeException("Friend request already pending");
                case ACCEPTED -> throw new RuntimeException("You are already friends");
                case REJECTED -> {
                    // cho phép gửi lại, nhưng cập nhật thay vì tạo mới
                    f.setUser(user);
                    f.setFriend(friend);
                    f.setStatus(FriendshipStatus.PENDING);
                    friendshipRepository.save(f);
                    return new FriendshipResponse("Friend request re-sent", FriendshipStatus.PENDING);
                }
            }
        }

        friendshipRepository.save(Friendship.builder()
                .user(user)
                .friend(friend)
                .status(FriendshipStatus.PENDING)
                .build());

        return new FriendshipResponse("Friend request sent", FriendshipStatus.PENDING);
    }

    @Override
    @Transactional
    public FriendshipResponse acceptRequest(Long userId, Long requesterId) {
        User user = userRepository.findById(userId).orElseThrow();
        User requester = userRepository.findById(requesterId).orElseThrow();

        Friendship f = friendshipRepository.findByUserAndFriend(requester, user)
                .orElseThrow(() -> new RuntimeException("No request found"));

        f.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(f);

        // tạo chiều ngược nếu chưa có
        friendshipRepository.findByUserAndFriend(user, requester)
                .orElseGet(() -> friendshipRepository.save(Friendship.builder()
                        .user(user)
                        .friend(requester)
                        .status(FriendshipStatus.ACCEPTED)
                        .build()));

        return new FriendshipResponse("Friend request accepted", FriendshipStatus.ACCEPTED);
    }

    @Override
    @Transactional
    public FriendshipResponse rejectRequest(Long userId, Long requesterId) {
        User user = userRepository.findById(userId).orElseThrow();
        User requester = userRepository.findById(requesterId).orElseThrow();

        Friendship f = friendshipRepository.findByUserAndFriend(requester, user)
                .orElseThrow(() -> new RuntimeException("No friend request found"));

        f.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(f);

        // Xóa chiều ngược lại nếu có
        friendshipRepository.findByUserAndFriend(user, requester)
                .ifPresent(friendshipRepository::delete);

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
    @Transactional
    public FriendshipResponse blockUser(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new RuntimeException("You cannot block yourself");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        // Tìm quan hệ hiện có giữa 2 người
        Optional<Friendship> existing = friendshipRepository.findByUserAndFriend(user, target)
                .or(() -> friendshipRepository.findByUserAndFriend(target, user));

        if (existing.isPresent()) {
            Friendship f = existing.get();
            f.setUser(user);  // người block
            f.setFriend(target);
            f.setStatus(FriendshipStatus.BLOCKED);
            friendshipRepository.save(f);
            return new FriendshipResponse("User blocked successfully", FriendshipStatus.BLOCKED);
        }

        // Chưa có, tạo mới
        Friendship block = Friendship.builder()
                .user(user)
                .friend(target)
                .status(FriendshipStatus.BLOCKED)
                .build();

        friendshipRepository.save(block);
        return new FriendshipResponse("User blocked successfully", FriendshipStatus.BLOCKED);
    }

    @Override
    @Transactional
    public FriendshipResponse unblockUser(Long userId, Long targetId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        Friendship friendship = friendshipRepository.findByUserAndFriend(user, target)
                .orElseThrow(() -> new RuntimeException("No blocked relationship found"));

        if (friendship.getStatus() != FriendshipStatus.BLOCKED) {
            throw new RuntimeException("This user is not blocked");
        }

        friendshipRepository.delete(friendship);
        return new FriendshipResponse("User unblocked successfully", FriendshipStatus.REJECTED);
    }

    @Override
    public List<UserProfileDto> getPendingRequests(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Friendship> requests = friendshipRepository.findByFriendAndStatus(user, FriendshipStatus.PENDING);
        return requests.stream()
                .map(Friendship::getUser) // người gửi lời mời
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserProfileDto> getBlockedUsers(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Friendship> blocked = friendshipRepository.findByUserAndStatus(user, FriendshipStatus.BLOCKED);
        return blocked.stream()
                .map(Friendship::getFriend)
                .map(userMapper::toDto)
                .collect(Collectors.toList());
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

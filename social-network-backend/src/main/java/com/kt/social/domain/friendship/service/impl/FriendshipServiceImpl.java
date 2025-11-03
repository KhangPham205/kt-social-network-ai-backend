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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Optional<Friendship> existing = friendshipRepository.findBySenderAndReceiver(user, friend)
                .or(() -> friendshipRepository.findBySenderAndReceiver(friend, user));

        if (existing.isPresent()) {
            Friendship f = existing.get();
            switch (f.getStatus()) {
                case BLOCKED -> throw new RuntimeException("You cannot send a request to a blocked user");
                case PENDING -> throw new RuntimeException("Friend request already pending");
                case ACCEPTED -> throw new RuntimeException("You are already friends");
                case REJECTED -> {
                    // cho phép gửi lại, nhưng cập nhật thay vì tạo mới
                    f.setSender(user);
                    f.setReceiver(friend);
                    f.setStatus(FriendshipStatus.PENDING);
                    friendshipRepository.save(f);
                    return new FriendshipResponse("Friend request re-sent", FriendshipStatus.PENDING);
                }
            }
        }

        friendshipRepository.save(Friendship.builder()
                .sender(user)
                .receiver(friend)
                .status(FriendshipStatus.PENDING)
                .build());

        return new FriendshipResponse("Friend request sent", FriendshipStatus.PENDING);
    }

    @Override
    @Transactional
    public FriendshipResponse acceptRequest(Long senderId, Long receiverId) {
        User sender = userRepository.findById(senderId).orElseThrow();
        User receiver = userRepository.findById(receiverId).orElseThrow();

        Friendship f = friendshipRepository.findBySenderAndReceiver(sender, receiver)
                .orElseThrow(() -> new RuntimeException("No request found"));

        f.setStatus(FriendshipStatus.FRIEND);
        friendshipRepository.save(f);

        return new FriendshipResponse("Friend request accepted", FriendshipStatus.FRIEND);
    }

    @Override
    @Transactional
    public FriendshipResponse rejectRequest(Long senderId, Long receiverId) {
        User sender = userRepository.findById(senderId).orElseThrow();
        User receiver = userRepository.findById(receiverId).orElseThrow();

        friendshipRepository.findBySenderAndReceiver(sender, receiver)
                .ifPresent(friendshipRepository::delete);

        return new FriendshipResponse("Friend request rejected", FriendshipStatus.REJECTED);
    }

    @Override
    @Transactional
    public FriendshipResponse unfriend(Long userId, Long friendId) {
        User user = userRepository.findById(userId).orElseThrow();
        User friend = userRepository.findById(friendId).orElseThrow();

        friendshipRepository.findBySenderAndReceiver(user, friend)
                .ifPresent(friendshipRepository::delete);
        friendshipRepository.findBySenderAndReceiver(friend, user)
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
        Optional<Friendship> existing = friendshipRepository.findBySenderAndReceiver(user, target)
                .or(() -> friendshipRepository.findBySenderAndReceiver(target, user));

        if (existing.isPresent()) {
            Friendship f = existing.get();
            f.setSender(user);  // người block
            f.setReceiver(target);
            f.setStatus(FriendshipStatus.BLOCKED);
            friendshipRepository.save(f);
            return new FriendshipResponse("User blocked successfully", FriendshipStatus.BLOCKED);
        }

        // Chưa có, tạo mới
        Friendship block = Friendship.builder()
                .sender(user)
                .receiver(target)
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

        Friendship friendship = friendshipRepository.findBySenderAndReceiver(user, target)
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
        List<Friendship> requests = friendshipRepository.findByReceiverAndStatus(user, FriendshipStatus.PENDING);
        return requests.stream()
                .map(Friendship::getSender) // người gửi lời mời
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserProfileDto> getBlockedUsers(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Friendship> blocked = friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.BLOCKED);
        return blocked.stream()
                .map(Friendship::getReceiver)
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FriendshipResponse unsendRequest(Long userId, Long targetId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User friend = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        // Tìm lời mời giữa 2 người
        Optional<Friendship> existing = friendshipRepository.findBySenderAndReceiver(user, friend)
                .or(() -> friendshipRepository.findBySenderAndReceiver(friend, user));

        if (existing.isEmpty()) {
            throw new RuntimeException("No friend request found between users");
        }

        Friendship friendship = existing.get();

        // Chỉ người gửi mới được hủy lời mời
        if (!friendship.getSender().getId().equals(userId)) {
            throw new RuntimeException("You cannot unsend a request you didn’t send");
        }

        // Chỉ hủy nếu trạng thái là PENDING
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("Cannot unsend a request that is not pending");
        }

        friendshipRepository.delete(friendship);
        return new FriendshipResponse("Friend request unsent", null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserProfileDto> getSentRequests(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Friendship> sentRequests = friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.PENDING);

        return sentRequests.stream()
                .map(Friendship::getReceiver)
                .map(userMapper::toDto)
                .toList();
    }

    @Override
    public List<UserProfileDto> getFriends(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Friendship> friendships = friendshipRepository.findBySenderAndStatus(user, FriendshipStatus.ACCEPTED);
        return friendships.stream()
                .map(Friendship::getReceiver)
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }
}

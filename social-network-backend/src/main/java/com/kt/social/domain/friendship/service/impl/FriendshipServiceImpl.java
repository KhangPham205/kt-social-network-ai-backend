package com.kt.social.domain.friendship.service.impl;

import com.kt.social.common.service.BaseFilterService;
import com.kt.social.common.vo.PageVO;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl extends BaseFilterService<Friendship, UserProfileDto> implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    // --------------------------- Friend Actions ---------------------------

    @Override
    @Transactional
    public FriendshipResponse sendRequest(Long userId, Long targetId) {
        if (userId.equals(targetId))
            throw new IllegalArgumentException("You cannot send a friend request to yourself");

        User sender = getUser(userId);
        User receiver = getUser(targetId);

        Optional<Friendship> existing = friendshipRepository.findBySenderAndReceiver(sender, receiver)
                .or(() -> friendshipRepository.findBySenderAndReceiver(receiver, sender));

        if (existing.isPresent()) {
            Friendship f = existing.get();
            switch (f.getStatus()) {
                case BLOCKED -> throw new RuntimeException("You cannot send a request to a blocked user");
                case PENDING -> throw new RuntimeException("Friend request already pending");
                case FRIEND -> throw new RuntimeException("You are already friends");
                case REJECTED -> {
                    f.setSender(sender);
                    f.setReceiver(receiver);
                    f.setStatus(FriendshipStatus.PENDING);
                    friendshipRepository.save(f);
                    return new FriendshipResponse("Friend request re-sent", FriendshipStatus.PENDING, userId, targetId);
                }
            }
        }

        friendshipRepository.save(Friendship.builder()
                .sender(sender)
                .receiver(receiver)
                .status(FriendshipStatus.PENDING)
                .build());

        return new FriendshipResponse("Friend request sent", FriendshipStatus.PENDING, userId, targetId);
    }

    @Override
    @Transactional
    public FriendshipResponse acceptRequest(Long senderId, Long receiverId) {
        Friendship f = getFriendship(senderId, receiverId);
        f.setStatus(FriendshipStatus.FRIEND);
        friendshipRepository.save(f);
        return new FriendshipResponse("Friend request accepted", FriendshipStatus.FRIEND, senderId, receiverId);
    }

    @Override
    @Transactional
    public FriendshipResponse rejectRequest(Long senderId, Long receiverId) {
        friendshipRepository.findBySenderAndReceiver(getUser(senderId), getUser(receiverId))
                .ifPresent(friendshipRepository::delete);
        return new FriendshipResponse("Friend request rejected", FriendshipStatus.REJECTED, senderId, receiverId);
    }

    @Override
    @Transactional
    public FriendshipResponse unfriend(Long userId, Long friendId) {
        User u1 = getUser(userId);
        User u2 = getUser(friendId);

        friendshipRepository.findBySenderAndReceiver(u1, u2).ifPresent(friendshipRepository::delete);
        friendshipRepository.findBySenderAndReceiver(u2, u1).ifPresent(friendshipRepository::delete);

        return new FriendshipResponse("Unfriended successfully", FriendshipStatus.REJECTED, userId, friendId);
    }

    @Override
    @Transactional
    public FriendshipResponse blockUser(Long userId, Long targetId) {
        if (userId.equals(targetId))
            throw new RuntimeException("You cannot block yourself");

        User user = getUser(userId);
        User target = getUser(targetId);

        Friendship f = friendshipRepository.findBySenderAndReceiver(user, target)
                .or(() -> friendshipRepository.findBySenderAndReceiver(target, user))
                .orElse(Friendship.builder().sender(user).receiver(target).build());

        f.setSender(user);
        f.setReceiver(target);
        f.setStatus(FriendshipStatus.BLOCKED);
        friendshipRepository.save(f);

        return new FriendshipResponse("User blocked successfully", FriendshipStatus.BLOCKED, userId, targetId);
    }

    @Override
    @Transactional
    public FriendshipResponse unblockUser(Long userId, Long targetId) {
        Friendship friendship = friendshipRepository.findBySenderAndReceiver(getUser(userId), getUser(targetId))
                .orElseThrow(() -> new RuntimeException("No blocked relationship found"));

        if (friendship.getStatus() != FriendshipStatus.BLOCKED)
            throw new RuntimeException("This user is not blocked");

        friendshipRepository.delete(friendship);
        return new FriendshipResponse("User unblocked successfully", FriendshipStatus.REJECTED, userId, targetId);
    }

    @Override
    @Transactional
    public FriendshipResponse unsendRequest(Long userId, Long targetId) {
        Friendship f = friendshipRepository.findBySenderAndReceiver(getUser(userId), getUser(targetId))
                .or(() -> friendshipRepository.findBySenderAndReceiver(getUser(targetId), getUser(userId)))
                .orElseThrow(() -> new RuntimeException("No friend request found"));

        if (!f.getSender().getId().equals(userId))
            throw new RuntimeException("You cannot unsend a request you didn’t send");

        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new RuntimeException("Cannot unsend a request that is not pending");

        friendshipRepository.delete(f);
        return new FriendshipResponse("Friend request unsent", null, userId, targetId);
    }

    // --------------------------- Pagination + Filter (RSQL) ---------------------------

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserProfileDto> getFriends(Long userId, String filter, Pageable pageable) {
        User user = getUser(userId);
        Specification<Friendship> base = (root, q, cb) -> cb.and(
                cb.equal(root.get("status"), FriendshipStatus.FRIEND),
                cb.or(
                        cb.equal(root.get("sender"), user),
                        cb.equal(root.get("receiver"), user)
                )
        );

        return filterEntity(Friendship.class, filter, pageable,
                friendshipRepository, f -> userMapper.toDto(f.getReceiver()), base);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserProfileDto> getPendingRequests(Long userId, String filter, Pageable pageable) {
        User user = getUser(userId);
        Specification<Friendship> base = (root, q, cb) ->
                cb.and(cb.equal(root.get("receiver"), user),
                        cb.equal(root.get("status"), FriendshipStatus.PENDING));

        return filterEntity(Friendship.class, filter, pageable,
                friendshipRepository, f -> userMapper.toDto(f.getSender()), base);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserProfileDto> getSentRequests(Long userId, String filter, Pageable pageable) {
        User user = getUser(userId);
        Specification<Friendship> base = (root, q, cb) ->
                cb.and(cb.equal(root.get("sender"), user),
                        cb.equal(root.get("status"), FriendshipStatus.PENDING));

        return filterEntity(Friendship.class, filter, pageable,
                friendshipRepository, f -> userMapper.toDto(f.getReceiver()), base);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserProfileDto> getBlockedUsers(Long userId, String filter, Pageable pageable) {
        User user = getUser(userId);
        Specification<Friendship> base = (root, q, cb) ->
                cb.and(cb.equal(root.get("sender"), user),
                        cb.equal(root.get("status"), FriendshipStatus.BLOCKED));

        return filterEntity(Friendship.class, filter, pageable,
                friendshipRepository, f -> userMapper.toDto(f.getReceiver()), base);
    }

    // --------------------------- Helpers ---------------------------

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Friendship getFriendship(Long senderId, Long receiverId) {
        return friendshipRepository.findBySenderAndReceiver(getUser(senderId), getUser(receiverId))
                .orElseThrow(() -> new RuntimeException("Friendship not found"));
    }
}
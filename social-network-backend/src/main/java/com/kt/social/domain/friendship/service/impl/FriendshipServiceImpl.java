package com.kt.social.domain.friendship.service.impl;

import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.service.BaseFilterService;
import com.kt.social.common.utils.BlockUtils;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.model.Friendship;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.friendship.service.FriendshipService;
import com.kt.social.domain.message.dto.ConversationCreateRequest;
import com.kt.social.domain.message.service.ConversationService;
import com.kt.social.domain.user.dto.UserProfileDto;
import com.kt.social.domain.user.dto.UserRelationDto;
import com.kt.social.domain.user.mapper.UserMapper;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.model.UserRela;
import com.kt.social.domain.user.repository.UserRelaRepository;
import com.kt.social.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl extends BaseFilterService<Friendship, UserRelationDto> implements FriendshipService {

    private final BlockUtils blockUtils;
    private final ConversationService conversationService;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserRelaRepository userRelaRepository;
    private final UserMapper userMapper;

    // --------------------------- Friend Actions ---------------------------

    @Override
    @Transactional
    public FriendshipResponse sendRequest(Long userId, Long targetId) {
        if (userId.equals(targetId))
            throw new BadRequestException("You cannot send a friend request to yourself");

        if (blockUtils.isBlocked(userId, targetId) || blockUtils.isBlocked(targetId, userId)) {
            throw new BadRequestException("Cannot send request — one of you has blocked the other");
        }

        User sender = getUser(userId);
        User receiver = getUser(targetId);

        Optional<Friendship> existing = friendshipRepository.findBySenderAndReceiver(sender, receiver)
                .or(() -> friendshipRepository.findBySenderAndReceiver(receiver, sender));

        if (existing.isPresent()) {
            Friendship f = existing.get();
            switch (f.getStatus()) {
                case BLOCKED -> throw new BadRequestException("You cannot send a request to a blocked user");
                case PENDING -> throw new BadRequestException("Friend request already pending");
                case FRIEND -> throw new BadRequestException("You are already friends");
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

        if (f.getStatus() != FriendshipStatus.PENDING) {
            throw new BadRequestException("Cannot approve a request that does not have status PENDING");
        }

        if (blockUtils.isBlocked(senderId, receiverId) || blockUtils.isBlocked(receiverId, senderId)) {
            throw new BadRequestException("Cannot send request — one of you has blocked the other");
        }

        f.setStatus(FriendshipStatus.FRIEND);
        friendshipRepository.save(f);

        User sender = getUser(senderId);
        User receiver = getUser(receiverId);

        // follow hai chiều khi trở thành bạn bè
        if (!userRelaRepository.existsByFollowerAndFollowing(sender, receiver))
            userRelaRepository.save(UserRela.builder().follower(sender).following(receiver).build());
        if (!userRelaRepository.existsByFollowerAndFollowing(receiver, sender))
            userRelaRepository.save(UserRela.builder().follower(receiver).following(sender).build());

        // Tạo conversation giữa 2 người khi là bạn bè
        conversationService.findOrCreateDirectConversation(senderId, receiverId);

        return new FriendshipResponse("Friend request accepted", FriendshipStatus.FRIEND, senderId, receiverId);
    }

    @Override
    @Transactional
    public FriendshipResponse rejectRequest(Long senderId, Long receiverId) {
        Friendship f = getFriendship(senderId, receiverId);

        if (f.getStatus() != FriendshipStatus.PENDING) {
            throw new BadRequestException("Cannot reject a request that does not have status PENDING");
        }

        friendshipRepository.delete(f);
        return new FriendshipResponse("Friend request rejected", FriendshipStatus.REJECTED, senderId, receiverId);
    }

    @Override
    @Transactional
    public FriendshipResponse unfriend(Long userId, Long friendId) {
        User u1 = getUser(userId);
        User u2 = getUser(friendId);

        Optional<Friendship> f1 = friendshipRepository.findBySenderAndReceiver(u1, u2);
        Optional<Friendship> f2 = friendshipRepository.findBySenderAndReceiver(u2, u1);

        if (f1.isEmpty() && f2.isEmpty()) {
            throw new ResourceNotFoundException("Bạn không phải là bạn bè với người dùng này");
        }

        f1.ifPresent(friendshipRepository::delete);
        f2.ifPresent(friendshipRepository::delete);

        userRelaRepository.deleteByFollowerAndFollowing(u1, u2);
        userRelaRepository.deleteByFollowerAndFollowing(u2, u1);

        return new FriendshipResponse("Unfriended successfully", FriendshipStatus.REJECTED, userId, friendId);
    }

    @Override
    @Transactional
    public FriendshipResponse blockUser(Long userId, Long targetId) {
        if (userId.equals(targetId))
            throw new BadRequestException("You cannot block yourself");

        User user = getUser(userId);
        User target = getUser(targetId);

        // Xóa quan hệ friend + follow hai chiều
        friendshipRepository.findBySenderAndReceiver(user, target).ifPresent(friendshipRepository::delete);
        friendshipRepository.findBySenderAndReceiver(target, user).ifPresent(friendshipRepository::delete);
        userRelaRepository.deleteByFollowerAndFollowing(user, target);
        userRelaRepository.deleteByFollowerAndFollowing(target, user);

        // Tạo mới bản ghi BLOCK
        Friendship f = friendshipRepository.findBySenderAndReceiver(user, target)
                .orElse(Friendship.builder()
                        .sender(user)
                        .receiver(target)
                        .build()
                );

        f.setStatus(FriendshipStatus.BLOCKED);
        friendshipRepository.save(f);

        return new FriendshipResponse("User blocked successfully", FriendshipStatus.BLOCKED, userId, targetId);
    }

    @Override
    @Transactional
    public FriendshipResponse unblockUser(Long userId, Long targetId) {
        Friendship friendship = friendshipRepository.findBySenderAndReceiver(getUser(userId), getUser(targetId))
                .orElseThrow(() -> new ResourceNotFoundException("No blocked relationship found"));

        if (friendship.getStatus() != FriendshipStatus.BLOCKED)
            throw new BadRequestException("This user is not blocked");

        friendshipRepository.delete(friendship);
        return new FriendshipResponse("User unblocked successfully", FriendshipStatus.REJECTED, userId, targetId);
    }

    @Override
    @Transactional
    public FriendshipResponse unsendRequest(Long userId, Long targetId) {
        Friendship f = friendshipRepository.findBySenderAndReceiver(getUser(userId), getUser(targetId))
                .or(() -> friendshipRepository.findBySenderAndReceiver(getUser(targetId), getUser(userId)))
                .orElseThrow(() -> new ResourceNotFoundException("No friend request found"));

        if (!f.getSender().getId().equals(userId))
            throw new AccessDeniedException("You cannot unsend a request you didn’t send");

        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new BadRequestException("Cannot unsend a request that is not pending");

        friendshipRepository.delete(f);
        return new FriendshipResponse("Friend request unsent", null, userId, targetId);
    }

    // --------------------------- Pagination + Filter (RSQL) ---------------------------

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserRelationDto> getFriends(Long userId, String filter, Pageable pageable) {
        User user = getUser(userId);

        Specification<Friendship> base = (root, q, cb) -> cb.and(
                cb.equal(root.get("status"), FriendshipStatus.FRIEND),
                cb.or(
                        cb.equal(root.get("sender"), user),
                        cb.equal(root.get("receiver"), user)
                )
        );

        return filterEntity(
                Friendship.class,
                filter,
                pageable,
                friendshipRepository,
                friendship -> {
                    User friend = friendship.getSender().equals(user)
                            ? friendship.getReceiver()
                            : friendship.getSender();
                    return toRelationDto(user, friend);
                },
                base
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserRelationDto> getPendingRequests(Long userId, String filter, Pageable pageable) {
        User user = getUser(userId);
        Specification<Friendship> base = (root, q, cb) ->
                cb.and(cb.equal(root.get("receiver"), user),
                        cb.equal(root.get("status"), FriendshipStatus.PENDING));

        return filterEntity(
                Friendship.class,
                filter,
                pageable,
                friendshipRepository,
                f -> toRelationDto(user, f.getSender()),
                base
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserRelationDto> getSentRequests(Long userId, String filter, Pageable pageable) {
        User user = getUser(userId);
        Specification<Friendship> base = (root, q, cb) ->
                cb.and(cb.equal(root.get("sender"), user),
                        cb.equal(root.get("status"), FriendshipStatus.PENDING));

        return filterEntity(
                Friendship.class,
                filter,
                pageable,
                friendshipRepository,
                f -> toRelationDto(user, f.getReceiver()),
                base
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserRelationDto> getBlockedUsers(Long userId, String filter, Pageable pageable) {
        User user = getUser(userId);
        Specification<Friendship> base = (root, q, cb) ->
                cb.and(cb.equal(root.get("sender"), user),
                        cb.equal(root.get("status"), FriendshipStatus.BLOCKED));

        return filterEntity(
                Friendship.class,
                filter,
                pageable,
                friendshipRepository,
                f -> toRelationDto(user, f.getReceiver()),
                base
        );
    }

    // --------------------------- Helpers ---------------------------

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Friendship getFriendship(Long senderId, Long receiverId) {
        return friendshipRepository.findBySenderAndReceiver(getUser(senderId), getUser(receiverId))
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));
    }

    private UserRelationDto toRelationDto(User viewer, User target) {
        boolean isFollowing = userRelaRepository.existsByFollowerAndFollowing(viewer, target);
        boolean isFollowedBy = userRelaRepository.existsByFollowerAndFollowing(target, viewer);

        var friendship = friendshipRepository.findBySenderAndReceiver(viewer, target)
                .or(() -> friendshipRepository.findBySenderAndReceiver(target, viewer))
                .map(f -> FriendshipResponse.builder()
                        .status(f.getStatus())
                        .senderId(f.getSender().getId())
                        .receiverId(f.getReceiver().getId())
                        .build())
                .orElse(FriendshipResponse.builder().build());

        UserProfileDto base = userMapper.toDto(target);

        return UserRelationDto.builder()
                .id(base.getId())
                .displayName(base.getDisplayName())
                .avatarUrl(base.getAvatarUrl())
                .isActive(base.getIsActive())
                .bio(base.getBio())
                .favorites(base.getFavorites())
                .dateOfBirth(base.getDateOfBirth())
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .friendship(friendship)
                .build();
    }
}
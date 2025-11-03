package com.kt.social.domain.friendship.repository;

import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.model.Friendship;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findBySenderAndReceiver(User sender, User receiver);

    Page<Friendship> findBySenderAndStatus(User sender, FriendshipStatus status, Pageable pageable);

    Page<Friendship> findByReceiverAndStatus(User receiver, FriendshipStatus status, Pageable pageable);

    boolean existsBySenderAndReceiverAndStatus(User user, User friend, FriendshipStatus status);

    default boolean existsByUserAndFriendAndStatusApproved(User user, User friend) {
        return existsBySenderAndReceiverAndStatus(user, friend, FriendshipStatus.ACCEPTED);
    }
}
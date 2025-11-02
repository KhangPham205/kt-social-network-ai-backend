package com.kt.social.domain.friendship.repository;

import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.model.Friendship;
import com.kt.social.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findByUserAndFriend(User user, User friend);

    List<Friendship> findByUserAndStatus(User user, FriendshipStatus status);

    List<Friendship> findByFriendAndStatus(User friend, FriendshipStatus status);

    boolean existsByUserAndFriendAndStatus(User user, User friend, FriendshipStatus status);

    default boolean existsByUserAndFriendAndStatusApproved(User user, User friend) {
        return existsByUserAndFriendAndStatus(user, friend, FriendshipStatus.ACCEPTED);
    }
}
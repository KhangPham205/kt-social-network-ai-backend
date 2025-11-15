package com.kt.social.domain.friendship.repository;

import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.model.Friendship;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long>, JpaSpecificationExecutor<Friendship> {

    Optional<Friendship> findBySenderAndReceiver(User sender, User receiver);

    Page<Friendship> findBySenderAndStatus(User sender, FriendshipStatus status, Pageable pageable);

    Page<Friendship> findByReceiverAndStatus(User receiver, FriendshipStatus status, Pageable pageable);

    boolean existsBySenderAndReceiverAndStatus(User user, User friend, FriendshipStatus status);

    default boolean existsByUserAndFriendAndStatusApproved(User user, User friend) {
        return existsBySenderAndReceiverAndStatus(user, friend, FriendshipStatus.FRIEND);
    }

    // Lấy danh sách bạn bè khi mình là sender
    @Query("SELECT f.receiver FROM Friendship f WHERE f.sender = :user AND f.status = 'FRIEND'")
    List<User> findAcceptedFriendsAsSender(@Param("user") User user);

    // Lấy danh sách bạn bè khi mình là receiver
    @Query("SELECT f.sender FROM Friendship f WHERE f.receiver = :user AND f.status = 'FRIEND'")
    List<User> findAcceptedFriendsAsReceiver(@Param("user") User user);

    // Helper mặc định gộp cả 2 chiều
    default List<User> findAllAcceptedFriends(User user) {
        List<User> result = new ArrayList<>();
        result.addAll(findAcceptedFriendsAsSender(user));
        result.addAll(findAcceptedFriendsAsReceiver(user));
        return result;
    }

    @Query("""
    SELECT CASE
        WHEN f.sender.id = :userId THEN f.receiver.id
        ELSE f.sender.id
    END
    FROM Friendship f
    WHERE (f.sender.id = :userId)
      AND f.status = 'BLOCKED'
""")
    List<Long> findBlockedUserIds(@Param("userId") Long userId);

    @Query("""
    SELECT CASE
        WHEN f.sender.id = :userId THEN f.receiver.id
        ELSE f.sender.id
    END
    FROM Friendship f
    WHERE (f.receiver.id = :userId)
      AND f.status = 'BLOCKED'
""")
    List<Long> findBlockedUserIdsByTarget(@Param("userId") Long id);

    @Query("""
    SELECT f 
    FROM Friendship f 
    WHERE (f.sender.id = :viewerId 
            AND f.receiver.id IN :targetIds) 
        OR (f.sender.id IN :targetIds 
            AND f.receiver.id = :viewerId
        )
""")
    List<Friendship> findFriendshipsBetween(@Param("viewerId") Long viewerId, @Param("targetIds") Set<Long> targetIds);

    @Query("""
    SELECT
        CASE WHEN COUNT(f) > 0 THEN true ELSE false END
    FROM Friendship f
    WHERE ((f.sender = :user1 AND f.receiver = :user2) OR (f.sender = :user2 AND f.receiver = :user1))
        AND f.status = 'FRIEND'
""")
    boolean existsActiveFriendship(@Param("user1") User user1, @Param("user2") User user2);
}
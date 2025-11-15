package com.kt.social.domain.user.repository;

import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.model.UserRela;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRelaRepository extends JpaRepository<UserRela, Long> {
    List<UserRela> findByFollower(User follower);
    List<UserRela> findByFollowing(User following);
    Optional<UserRela> findByFollowerAndFollowing(User follower, User following);
    boolean existsByFollowerAndFollowing(User follower, User following);
    void deleteByFollowerAndFollowing(User follower, User following);
    Page<UserRela> findByFollowing(User following, Pageable pageable);
    Page<UserRela> findByFollower(User follower, Pageable pageable);

    @Query("SELECT ur.following.id FROM UserRela ur WHERE ur.follower.id = :viewerId AND ur.following.id IN :targetIds")
    Set<Long> findFollowingIds(@Param("viewerId") Long viewerId, @Param("targetIds") Set<Long> targetIds);

    @Query("SELECT ur.follower.id FROM UserRela ur WHERE ur.following.id = :viewerId AND ur.follower.id IN :targetIds")
    Set<Long> findFollowerIds(@Param("viewerId") Long viewerId, @Param("targetIds") Set<Long> targetIds);
}

package com.kt.social.domain.user.repository;

import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.model.UserRela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRelaRepository extends JpaRepository<UserRela, Long> {
    List<UserRela> findByFollower(User follower);
    List<UserRela> findByFollowing(User following);
    Optional<UserRela> findByFollowerAndFollowing(User follower, User following);
    boolean existsByFollowerAndFollowing(User follower, User following);
    void deleteByFollowerAndFollowing(User follower, User following);
}

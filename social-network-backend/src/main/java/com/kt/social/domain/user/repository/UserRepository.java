package com.kt.social.domain.user.repository;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.domain.moderation.dto.UserModerationResponse;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    User findByCredential(UserCredential cred);
    List<User> findByIdIn(List<Long> ids);
    User findByCredentialUsername(String username);

    @Query("SELECT new com.kt.social.domain.moderation.dto.UserModerationResponse(" +
            "u.id, " +
            "c.username, " +
            "c.email, " +
            "u.displayName, " +
            "u.avatarUrl, " +
            "c.status, " +
            "COUNT(r)) " +        // Đếm số lượng report liên quan đến user này
            "FROM User u " +
            "JOIN u.credential c " +
            // 🔥 JOIN CHÍNH XÁC: Join vào cột targetUserId mới thêm
            "LEFT JOIN Report r ON u.id = r.targetUserId " +
            "GROUP BY u.id, c.username, c.email, u.displayName, u.avatarUrl, c.status")
    Page<UserModerationResponse> findAllUsersWithReportCount(Pageable pageable);
}

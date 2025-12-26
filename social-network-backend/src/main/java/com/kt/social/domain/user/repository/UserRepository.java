package com.kt.social.domain.user.repository;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.domain.moderation.dto.UserModerationResponse;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    User findByCredential(UserCredential cred);

    @Query("SELECT new com.kt.social.domain.moderation.dto.UserModerationResponse(" +
            "u.id, " +
            "c.username, " +
            "c.email, " +
            "u.displayName, " +
            "u.avatarUrl, " +
            "c.status, " +
            "COUNT(r)) " +
            "FROM User u " +
            "JOIN u.credential c " +
            "LEFT JOIN Report r ON u.id = r.targetUserId " +
            "WHERE (:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(c.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.displayName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "GROUP BY u.id, c.username, c.email, u.displayName, u.avatarUrl, c.status " +
            "HAVING COUNT(r) > 0 OR c.status = com.kt.social.auth.enums.AccountStatus.BLOCKED " +
            "ORDER BY c.status ASC, COUNT(r) DESC")
    Page<UserModerationResponse> findAllUsersWithReportCount(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
    SELECT to_char(created_at, 'YYYY-MM-DD') as date, COUNT(*) 
    FROM users 
    WHERE created_at >= NOW() - INTERVAL '7 days'
    GROUP BY date 
    ORDER BY date
""", nativeQuery = true)
    List<Object[]> countNewUsersLast7Days();

    @Query(value = """
        SELECT CAST(EXTRACT(MONTH FROM created_at) AS INTEGER) as time_unit, COUNT(*) 
        FROM users 
        WHERE EXTRACT(YEAR FROM created_at) = :year 
        GROUP BY time_unit
    """, nativeQuery = true)
    List<Object[]> countByYear(@Param("year") int year);

    @Query(value = """
        SELECT CAST(EXTRACT(DAY FROM created_at) AS INTEGER) as time_unit, COUNT(*) 
        FROM users 
        WHERE EXTRACT(MONTH FROM created_at) = :month 
          AND EXTRACT(YEAR FROM created_at) = :year 
        GROUP BY time_unit
    """, nativeQuery = true)
    List<Object[]> countByMonth(@Param("month") int month, @Param("year") int year);
}

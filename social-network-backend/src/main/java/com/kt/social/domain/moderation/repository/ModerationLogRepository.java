package com.kt.social.domain.moderation.repository;

import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.react.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long>, JpaSpecificationExecutor<ModerationLog> {
    @Query("SELECT m FROM ModerationLog m " +
            "WHERE m.targetType = :type " +
            "AND m.targetId = :id " +
            "AND (:filter IS NULL OR :filter = '' OR " +
            "     LOWER(m.reason) LIKE LOWER(CONCAT('%', :filter, '%')) OR " +
            "     LOWER(m.action) LIKE LOWER(CONCAT('%', :filter, '%')))" +
            "ORDER BY m.createdAt DESC")
    Page<ModerationLog> findHistory(@Param("type") TargetType type,
                                    @Param("id") String id,
                                    @Param("filter") String filter,
                                    Pageable pageable);

    @Query("SELECT m.reason, COUNT(m) FROM ModerationLog m GROUP BY m.reason")
    List<Object[]> countByReason();

    // Đếm số lượng chặn bởi AI (actor is null) vs Admin (actor is not null)
    @Query("SELECT COUNT(m) FROM ModerationLog m WHERE m.actor IS NULL")
    long countAutoBanned();

    @Query("SELECT COUNT(m) FROM ModerationLog m WHERE m.actor IS NOT NULL")
    long countManualBanned();
}

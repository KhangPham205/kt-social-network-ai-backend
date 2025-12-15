package com.kt.social.domain.report.repository;

import com.kt.social.common.dto.IdCount;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, TargetType targetType, Long targetId);

    @Query("SELECT r FROM Report r WHERE (" +
            "(r.targetType = 'USER' AND r.targetId = :userId) OR " +
            "(r.targetType = 'POST' AND r.targetId IN (SELECT p.id FROM Post p WHERE p.author.id = :userId)) OR " +
            "(r.targetType = 'COMMENT' AND r.targetId IN (SELECT c.id FROM Comment c WHERE c.author.id = :userId))" +
            ") ORDER BY r.createdAt DESC")
    Page<Report> findAllViolationsByUserId(
            @Param("userId") Long userId,
            @Param("status") ReportStatus status,
            Pageable pageable
    );

    long countByTargetUserId(Long userId);

    Page<Report> findByTargetUserId(Long userId, Pageable pageable);

    Page<Report> findByTargetTypeAndTargetId(TargetType targetType, Long targetId, Pageable pageable);

    @Query("SELECT r.targetId as id, COUNT(r) as count " +
            "FROM Report r " +
            "WHERE r.targetType = :type AND r.targetId IN :ids " +
            "GROUP BY r.targetId")
    List<IdCount> countByTargetTypeAndTargetIdIn(@Param("type") TargetType type, @Param("ids") List<Long> ids);
}

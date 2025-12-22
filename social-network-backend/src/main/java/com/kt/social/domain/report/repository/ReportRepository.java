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
import java.util.Set;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, TargetType targetType, String targetId);

    long countByTargetUserId(Long userId);

    Page<Report> findByTargetUserId(Long userId, Pageable pageable);

    Page<Report> findByTargetTypeAndTargetId(TargetType targetType, String targetId, Pageable pageable);

    @Query("SELECT r.targetId AS id, COUNT(r) AS count FROM Report r " +
            "WHERE r.targetType = :type AND r.targetId IN :ids " +
            "GROUP BY r.targetId")
    List<IdCount> countByTargetTypeAndTargetIdIn(@Param("type") TargetType type, @Param("ids") List<String> ids);

    boolean existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(String targetId, TargetType targetType);

    long countByStatus(ReportStatus status);

    @Query("SELECT r.targetId FROM Report r WHERE r.targetType = :type AND r.targetId IN :ids")
    Set<String> findReportedTargetIds(@Param("type") TargetType type, @Param("ids") List<String> ids);}
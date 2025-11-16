package com.kt.social.domain.report.repository;

import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.enums.ReportableType;
import com.kt.social.domain.report.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {
    // Tìm các report đang chờ (cho Moderator)
    Page<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

    // Kiểm tra xem user đã report nội dung này chưa (để tránh spam)
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportableType targetType, Long targetId);
}

package com.kt.social.domain.report.repository;

import com.kt.social.common.dto.IdCount;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.model.Complaint;
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
public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {
    // Kiểm tra xem đã có khiếu nại nào cho nội dung này chưa (tránh spam)
    boolean existsByTargetTypeAndTargetId(TargetType targetType, Long targetId);

    Page<Complaint> findByTargetTypeAndTargetId(TargetType targetType, String targetId, Pageable pageable);

    @Query("SELECT c.targetId as id, COUNT(c) as count " +
            "FROM Complaint c " +
            "WHERE c.targetType = :type AND c.targetId IN :ids " +
            "GROUP BY c.targetId")
    List<IdCount> countByTargetTypeAndTargetIdIn(@Param("type") TargetType type, @Param("ids") List<String> ids);
}
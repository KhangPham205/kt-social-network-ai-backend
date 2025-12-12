package com.kt.social.domain.report.repository;

import com.kt.social.domain.report.model.Complaint;
import com.kt.social.domain.report.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {
    boolean existsByReport(Report report);
}

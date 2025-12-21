package com.kt.social.domain.report.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.*;
import com.kt.social.domain.report.enums.ComplaintStatus;
import org.springframework.data.domain.Pageable;
import org.xbill.DNS.Update;

import java.util.List;

public interface ReportService {
    // Report
    ReportResponse createReport(Long reporterId, CreateReportRequest request);
    List<ReportResponse> updateReport(UpdateReportRequest request);
    ReportResponse getReportById(Long reportId);
    PageVO<ReportResponse> getReports(String filter, Pageable pageable);

    // Complaint
    ComplaintResponse createComplaint(CreateComplaintRequest request);
    ComplaintResponse updateComplaint(Long id, ComplaintStatus status);
    PageVO<ComplaintResponse> getComplaints(String filter, Pageable pageable);
    ComplaintResponse getComplaintById(Long id);

    PageVO<ReportResponse> getReportsByContent(Long targetId, TargetType targetType, Pageable pageable);
    PageVO<ComplaintResponse> getComplaintsByContent(Long targetId, TargetType targetType, Pageable pageable);

    //ComplaintResponse resolveComplaint(Long complaintId, ResolveComplaintRequest request);

}

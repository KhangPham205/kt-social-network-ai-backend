package com.kt.social.domain.report.controller;

import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.report.dto.*;
import com.kt.social.domain.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.COMPLAINTS)
@RequiredArgsConstructor
public class ComplaintController {

    private final ReportService reportService;

    // 1. User tạo khiếu nại
    @PostMapping
    @PreAuthorize("hasAuthority('COMPLAINT:CREATE')")
    public ResponseEntity<ComplaintResponse> createComplaint(@RequestBody CreateComplaintRequest request) {
        return ResponseEntity.ok(reportService.createComplaint(request));
    }

    // 2. Admin/Moderator giải quyết khiếu nại
    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('COMPLAINT:RESOLVE')")
    public ResponseEntity<ComplaintResponse> resolveComplaint(
            @PathVariable Long id,
            @RequestBody ResolveComplaintRequest request
    ) {
        return ResponseEntity.ok(reportService.resolveComplaint(id, request));
    }

    /**
     * Lấy danh sách khiếu nại (Complaints)
     * Hỗ trợ filter: status, username, reportId...
     * Ví dụ: /api/v1/admin/complaints?filter=status=='PENDING';username=='khang'&page=0&size=10
     */
    @GetMapping("/complaints")
    public ResponseEntity<PageVO<ComplaintResponse>> getComplaints(
            @RequestParam(required = false) String filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getComplaints(filter, pageable));
    }
}
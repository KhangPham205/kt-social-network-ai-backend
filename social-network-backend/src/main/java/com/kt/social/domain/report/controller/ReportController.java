package com.kt.social.domain.report.controller;

import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.report.dto.*;
import com.kt.social.domain.report.service.ReportService;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.REPORTS)
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final UserService userService;

    // 1. User tạo report
    @PostMapping
    @PreAuthorize("hasAuthority('REPORT:CREATE')")
    public ResponseEntity<ReportResponse> createReport(@RequestBody CreateReportRequest request) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(reportService.createReport(currentUserId, request));
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasAuthority('REPORT:VIEW_ALL')")
    public ResponseEntity<ReportResponse> getReportById(@PathVariable Long reportId) {
        return ResponseEntity.ok(reportService.getReportById(reportId));
    }

    // 2. Admin xem danh sách report (có filter)
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT:VIEW_ALL')")
    public ResponseEntity<PageVO<ReportResponse>> getReports(
            @RequestParam(required = false) String filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getReports(filter, pageable));
    }

    // 3. Admin xử lý report
    @PutMapping("/{id}/process")
    @PreAuthorize("hasAuthority('REPORT:PROCESS')")
    public ResponseEntity<ReportResponse> processReport(
            @PathVariable Long id,
            @RequestBody ProcessReportRequest request
    ) {
        return ResponseEntity.ok(reportService.processReport(id, request));
    }
}
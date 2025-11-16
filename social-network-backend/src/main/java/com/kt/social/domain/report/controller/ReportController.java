package com.kt.social.domain.report.controller;

import com.kt.social.common.constants.ApiConstants;
import com.kt.social.domain.report.dto.ReportDto;
import com.kt.social.domain.report.dto.ReportRequestDto;
import com.kt.social.domain.report.service.ReportService;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.REPORTS)
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority('REPORT:CREATE')")
    public ResponseEntity<ReportDto> createReport(@RequestBody ReportRequestDto request) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(reportService.createReport(currentUserId, request));
    }
}
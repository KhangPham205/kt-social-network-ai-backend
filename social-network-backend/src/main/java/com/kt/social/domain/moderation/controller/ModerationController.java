package com.kt.social.domain.moderation.controller; // (Package 'moderation')

import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.report.dto.ReportDto;
import com.kt.social.domain.report.dto.ReportReviewDto;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.service.ReportService;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.MODERATION)
@RequiredArgsConstructor
public class ModerationController {

    private final ReportService reportService;
    private final UserService userService;

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('MODERATION:READ')")
    public ResponseEntity<PageVO<ReportDto>> getReports(
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "status", defaultValue = "PENDING") ReportStatus status,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getReports(filter, status, pageable));
    }

    @PutMapping("/reports/{reportId}/review")
    @PreAuthorize("hasAuthority('MODERATION:UPDATE')") // Bảo vệ
    public ResponseEntity<ReportDto> reviewReport(
            @PathVariable Long reportId,
            @RequestBody ReportReviewDto request
    ) {
        Long reviewerId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(reportService.reviewReport(reportId, request, reviewerId));
    }
}
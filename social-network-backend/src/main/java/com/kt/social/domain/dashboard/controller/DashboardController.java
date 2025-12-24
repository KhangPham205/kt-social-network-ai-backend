package com.kt.social.domain.dashboard.controller;

import com.kt.social.common.constants.ApiConstants;
import com.kt.social.domain.dashboard.dto.*;
import com.kt.social.domain.dashboard.service.DashboardService;
import com.kt.social.common.dto.ApiResponse; // Wrapper response của bạn
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiConstants.API_V1 + "/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN:READ')")
public class DashboardController {

    private final DashboardService dashboardService;

    /* 1. API lấy tổng quan dashboard
     * Giúp trả lời: Hiện tại hệ thống thế nào? Có gì bất thường không?
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    /* 2. API lấy thống kê về moderation
     * Giúp trả lời: Hoạt động kiểm duyệt hiện tại thế nào? Có gì bất thường không?
     */
    @GetMapping("/moderation")
    public ResponseEntity<ModerationStats> getModerationStats() {
        return ResponseEntity.ok(dashboardService.getModerationStats());
    }

    /* 3. API lấy xu hướng người dùng mới theo thời gian
     * Giúp trả lời: Số lượng người dùng mới trong thời gian gần đây thế nào?
     */
    @GetMapping("/users/trend")
    public ResponseEntity<List<ChartData>> getUserTrend() {
        return ResponseEntity.ok(dashboardService.getNewUserTrend());
    }

    /* 4. API lấy thống kê chi tiết người dùng
     * Giúp trả lời: Thống kê chi tiết theo từng loại trong khoảng thời gian cụ thể
     */
    @GetMapping("/stats")
    public ResponseEntity<List<MultiSeriesChartData>> getDetailedStats(@ModelAttribute DashboardStatsRequest request) {
        return ResponseEntity.ok(dashboardService.getCombinedStats(request));
    }
}
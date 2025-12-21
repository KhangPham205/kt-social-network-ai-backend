package com.kt.social.domain.dashboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummary {
    private long totalUsers;
    private long totalPosts;
    private long totalComments;
    private long pendingReports; // Số report chưa xử lý
    private long blockedContentCount; // Tổng số nội dung bị chặn (bởi AI + Admin)
}

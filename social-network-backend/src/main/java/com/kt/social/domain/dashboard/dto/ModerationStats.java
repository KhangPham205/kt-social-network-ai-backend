package com.kt.social.domain.dashboard.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ModerationStats {
    private long totalAutoBanned; // AI chặn
    private long totalManualBanned; // Admin chặn
    private List<ChartData> violationTypeDistribution; // Phân bố theo lý do (Spam, Toxic...)
}

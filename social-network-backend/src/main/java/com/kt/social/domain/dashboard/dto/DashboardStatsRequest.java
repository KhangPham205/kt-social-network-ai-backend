package com.kt.social.domain.dashboard.dto;

import com.kt.social.domain.dashboard.enums.DashboardStatsType;
import com.kt.social.domain.react.enums.TargetType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsRequest {
    private DashboardStatsType type;
    private TargetType targetType;
    private String time;
}

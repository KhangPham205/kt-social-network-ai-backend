package com.kt.social.domain.dashboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MultiSeriesChartData {
    private String label;
    private long value;
}

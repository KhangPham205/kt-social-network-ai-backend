package com.kt.social.domain.dashboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartData {
    private String label; // Ví dụ: "2025-12-20", "Hate Speech"
    private long value;   // Ví dụ: 150, 45
}

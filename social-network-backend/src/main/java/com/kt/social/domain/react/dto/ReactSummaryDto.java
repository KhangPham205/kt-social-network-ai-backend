package com.kt.social.domain.react.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactSummaryDto {
    private Map<String, Long> counts;  // ví dụ: {"LIKE": 10, "LOVE": 3}
    private long total;
    private String currentUserReact;   // ví dụ: "LOVE"
}
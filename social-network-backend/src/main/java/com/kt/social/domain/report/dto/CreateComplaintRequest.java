package com.kt.social.domain.report.dto;

import com.kt.social.domain.react.enums.TargetType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateComplaintRequest {
    private String targetId;
    private TargetType targetType;
    private String reason;
}
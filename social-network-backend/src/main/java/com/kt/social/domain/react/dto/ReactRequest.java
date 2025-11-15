package com.kt.social.domain.react.dto;

import com.kt.social.domain.react.enums.TargetType;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactRequest {
    private Long targetId;
    private TargetType targetType; // "POST", "COMMENT", etc.
    private Long reactTypeId;
}
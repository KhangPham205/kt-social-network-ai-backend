package com.kt.social.domain.react.dto;

import com.kt.social.domain.react.enums.TargetType;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactResponse {
    private Long targetId;
    private TargetType targetType;
    private long reactCount;
    private String message;
}
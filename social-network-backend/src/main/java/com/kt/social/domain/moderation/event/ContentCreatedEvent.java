package com.kt.social.domain.moderation.event;

import com.kt.social.domain.react.enums.TargetType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContentCreatedEvent {
    private Long targetId;
    private TargetType targetType; // POST hoặc COMMENT
    private String content;
    private Long authorId;
}
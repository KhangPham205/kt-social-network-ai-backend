package com.kt.social.domain.moderation.dto;

import com.kt.social.domain.react.enums.TargetType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ModerationLogResponse {
    private Long id;
    private TargetType targetType;  // POST, COMMENT, USER
    private Long targetId;          // ID của đối tượng bị xử lý
    private String action;          // AUTO_BAN, ADMIN_BAN, ADMIN_RESTORE
    private String reason;          // Lý do

    // Thông tin người thực hiện (Actor)
    private Long actorId;
    private String actorName;       // Nếu null -> "System (AI)"
    private String actorAvatar;

    private Instant createdAt;
}
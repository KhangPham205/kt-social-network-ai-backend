package com.kt.social.domain.moderation.model;

import com.kt.social.common.entity.BaseEntity;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "moderation_logs")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private TargetType targetType; // POST, COMMENT, USER

    private String targetId;

    private String action; // AUTO_BAN, ADMIN_BAN, ADMIN_RESTORE

    private String reason;

    // Người thực hiện (Null nếu là AI System tự động)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;
}
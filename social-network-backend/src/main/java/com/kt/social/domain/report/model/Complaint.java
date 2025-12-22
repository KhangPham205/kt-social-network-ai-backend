package com.kt.social.domain.report.model;

import com.kt.social.common.entity.BaseEntity;
import com.kt.social.domain.react.enums.TargetType; // Nhớ import Enum này
import com.kt.social.domain.report.enums.ComplaintStatus;
import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "complaints")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint extends BaseEntity {

    @Column(name = "status")
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetType targetType; // POST, COMMENT

    @Column(nullable = false)
    private String targetId; // ID của Post hoặc Comment

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Người tạo khiếu nại

    @Column(columnDefinition = "TEXT")
    private String content; // Lý do: "Bài tôi không vi phạm..."

    @Column(columnDefinition = "TEXT")
    private String adminResponse;
}
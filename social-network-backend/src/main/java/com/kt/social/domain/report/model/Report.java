package com.kt.social.domain.report.model;

import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Thông tin từ User ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter; // Người báo cáo

    @Column(name = "status")
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetType targetType; // Loại bị báo cáo (POST, COMMENT, MESSAGE)

    @Column(nullable = false)
    private String targetId; // ID của post/comment/user bị báo cáo

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason; // Lý do

    private String customReason; // Ghi chú thêm (nếu reason=OTHER)

    private Boolean isBannedBySystem = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
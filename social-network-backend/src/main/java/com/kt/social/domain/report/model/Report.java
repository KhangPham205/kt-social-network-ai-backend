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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter; // Người báo cáo

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetType targetType; // Loại bị báo cáo (POST, COMMENT, USER, APPEAL)

    @Column(nullable = false)
    private Long targetId; // ID của post/comment/user bị báo cáo

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason; // Lý do

    private String customReason; // Ghi chú thêm (nếu reason=OTHER)

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    // --- Thông tin từ Moderator ---
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.PENDING; // Mặc định là PENDING

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<ReportHistory> history = new ArrayList<>(); // Lịch sử xử lý

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReportHistory implements Serializable {
        private Long actorId;       // ID người thực hiện (Admin)
        private String actorName;   // Tên admin (lưu cứng để lỡ xóa user vẫn còn tên)
        private ReportStatus oldStatus;
        private ReportStatus newStatus;
        private String note;        // Ghi chú của Admin
        private Instant timestamp;
    }
}
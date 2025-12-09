package com.kt.social.domain.report.model;

import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.enums.ReportableType;
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
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter; // Người báo cáo

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportableType targetType; // Loại bị báo cáo (POST, COMMENT, USER, APPEAL)

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer; // Moderator đã duyệt

    private String moderatorNotes; // Ghi chú của Mod

    private Instant reviewedAt;
}
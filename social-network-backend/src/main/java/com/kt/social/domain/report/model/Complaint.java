package com.kt.social.domain.report.model;

import com.kt.social.common.entity.BaseEntity;
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

    // Khiếu nại dựa trên Report nào (User thấy bài bị xóa do Report #123 -> Tạo khiếu nại cho #123)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // Người đi khiếu nại (thường là chủ bài viết)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String content; // Lý do khiếu nại: "Tôi không vi phạm vì..."

    @Column(columnDefinition = "TEXT")
    private String adminResponse; // Phản hồi của admin

    @Enumerated(EnumType.STRING)
    private ComplaintStatus status; // PENDING, RESOLVED_RESTORE (Khôi phục), RESOLVED_KEEP (Giữ nguyên phạt)
}
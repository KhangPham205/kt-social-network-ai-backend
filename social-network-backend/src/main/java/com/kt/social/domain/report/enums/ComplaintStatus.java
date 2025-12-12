package com.kt.social.domain.report.enums;

public enum ComplaintStatus {
    PENDING,            // Chờ xử lý
    APPROVED_RESTORE,   // Chấp nhận khiếu nại -> Khôi phục bài
    REJECTED_KEEP       // Từ chối -> Bài vẫn bị xóa
}

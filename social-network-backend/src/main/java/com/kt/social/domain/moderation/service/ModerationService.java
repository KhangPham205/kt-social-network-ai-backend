package com.kt.social.domain.moderation.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.admin.dto.ModerationMessageResponse;
import com.kt.social.domain.admin.dto.ModerationUserDetailResponse;
import com.kt.social.domain.moderation.dto.ModerationLogResponse;
import com.kt.social.domain.moderation.dto.UserModerationResponse;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.ReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

public interface ModerationService {
    ModerationUserDetailResponse getUserDetailForAdmin(Long userId);
    ModerationMessageResponse getMessageDetailForAdmin(String messageId);
    PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable);
    Page<UserModerationResponse> getUsersWithReportCount(int page, int size);

    @Transactional(readOnly = true)
    PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable);

    void restoreContent(Long id, TargetType targetType);
}

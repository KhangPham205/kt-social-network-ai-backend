package com.kt.social.domain.moderation.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.admin.dto.ModerationLogResponse;
import com.kt.social.domain.admin.dto.ModerationMessageResponse;
import com.kt.social.domain.admin.dto.ModerationUserDetailResponse;
import com.kt.social.domain.report.dto.ReportResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

public interface ModerationService {
    ModerationUserDetailResponse getUserDetailForAdmin(Long userId);
    ModerationMessageResponse getMessageDetailForAdmin(String messageId);
    PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable);

    @Transactional(readOnly = true)
    PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable);
}

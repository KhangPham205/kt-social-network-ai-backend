package com.kt.social.domain.admin.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.admin.dto.ModerationMessageResponse;
import com.kt.social.domain.admin.dto.ModerationUserDetailResponse;
import com.kt.social.domain.report.dto.ReportResponse;
import org.springframework.data.domain.Pageable;

public interface ModerationService {
    ModerationUserDetailResponse getUserDetailForAdmin(Long userId);
    ModerationMessageResponse getMessageDetailForAdmin(String messageId);
    PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable);
}

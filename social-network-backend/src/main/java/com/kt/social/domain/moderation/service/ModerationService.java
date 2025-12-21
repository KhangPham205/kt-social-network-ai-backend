package com.kt.social.domain.moderation.service;

import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.moderation.dto.*;
import com.kt.social.domain.comment.dto.CommentResponse;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.ReportResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

public interface ModerationService {
    ModerationUserDetailResponse getUserDetailForAdmin(Long userId);
    PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable);
    ModerationMessageResponse getMessageDetailForAdmin(String messageId);
    PageVO<UserModerationResponse> getUsersWithReportCount(Pageable pageable, String filter);
    PageVO<PostResponse> getFlaggedPosts(String filter, Pageable pageable);
    PageVO<CommentResponse> getFlaggedComments(String filter, Pageable pageable);
    PageVO<ModerationMessageResponse> getFlaggedMessages(String filter, Pageable pageable);
    PageVO<GroupedFlaggedMessageResponse> getGroupedFlaggedMessages(Pageable pageable);
    @Transactional(readOnly = true)
    PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable);

    void updateUserStatus(Long userId, AccountStatus newStatus, String reason);

    void blockContent(String id, TargetType type);
    void unblockContent(Long id, TargetType type);

    void validatePostContent(Post post);
    void validateImage(Long mediaId, byte[] imageBytes, String filename);
}

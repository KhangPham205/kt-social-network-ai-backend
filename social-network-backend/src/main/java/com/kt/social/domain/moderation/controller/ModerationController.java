package com.kt.social.domain.moderation.controller;

import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.admin.dto.ChangeStatusRequest;
import com.kt.social.domain.admin.dto.ModerationMessageResponse;
import com.kt.social.domain.admin.dto.ModerationUserDetailResponse;
import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import com.kt.social.domain.moderation.service.ModerationService;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.ReportResponse;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.MODERATION)
@RequiredArgsConstructor
public class ModerationController {

    private final UserService userService;
    private final ModerationService moderationService;
    private final PostRepository postRepository;
    private final ModerationLogRepository moderationLogRepository;

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAnyAuthority('USER:READ_SENSITIVE', 'MODERATION:ACCESS')")
    public ResponseEntity<ModerationUserDetailResponse> getUserDetail(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.getUserDetailForAdmin(id));
    }

    @GetMapping("/messages/{id}")
    @PreAuthorize("hasAnyAuthority('MESSAGE:READ_ANY', 'MODERATION:ACCESS')")
    public ResponseEntity<ModerationMessageResponse> getMessageDetail(@PathVariable String id) {
        return ResponseEntity.ok(moderationService.getMessageDetailForAdmin(id));
    }

    @GetMapping("/users/{id}/violations")
    @PreAuthorize("hasAnyAuthority('USER:READ_SENSITIVE', 'MODERATION:ACCESS')")
    public ResponseEntity<PageVO<ReportResponse>> getUserViolations(
            @PathVariable Long id,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(moderationService.getUserViolations(id, pageable));
    }
    /**
     * Endpoint Khóa tài khoản
     * Truy cập: Admin, Moderator
     */
    @PutMapping("/users/{id}/block")
    @PreAuthorize("hasAnyAuthority('USER:BLOCK', 'MODERATION:ACCESS')")
    public ResponseEntity<String> blockUser(
            @RequestBody ChangeStatusRequest request
    ) {
        String reason = (request != null) ? request.getReason() : "";
        userService.updateUserStatus(request.getId(), AccountStatus.BLOCKED, reason);
        return ResponseEntity.ok("User has been blocked successfully.");
    }

    /**
     * Endpoint Mở khóa tài khoản
     * Truy cập: Admin, Moderator
     */
    @PutMapping("/users/{id}/unblock")
    @PreAuthorize("hasAnyAuthority('USER:BLOCK', 'MODERATION:ACCESS')")
    public ResponseEntity<String> unblockUser(
            @RequestBody ChangeStatusRequest request
    ) {
        String reason = (request != null) ? request.getReason() : "";
        userService.updateUserStatus(request.getId(), AccountStatus.ACTIVE, reason);
        return ResponseEntity.ok("User has been unblocked successfully.");
    }

    @PutMapping("/{type}/{id}/restore")
    @PreAuthorize("hasAnyAuthority('POST:DELETE_ANY', 'MODERATION:ACCESS')")
    public ResponseEntity<String> restoreContent(
            @PathVariable TargetType type,
            @PathVariable Long id
    ) {
        User admin = userService.getCurrentUser();

        if (type == TargetType.POST) {
            Post post = postRepository.findById(id) // Cần repo tìm cả bài đã xóa (Native Query hoặc tắt filter)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
            post.setDeletedAt(null);
            post.setSystemBan(false);
            postRepository.save(post);
        } else {
            // Tương tự cho Comment
        }

        // Ghi Log Admin restore
        moderationLogRepository.save(ModerationLog.builder()
                .targetType(type)
                .targetId(id)
                .action("ADMIN_RESTORE")
                .actor(admin)
                .reason("Admin restored content")
                .build());

        return ResponseEntity.ok("Content restored successfully");
    }
}
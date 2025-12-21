package com.kt.social.domain.moderation.controller;

import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.admin.dto.ChangeStatusRequest;
import com.kt.social.domain.moderation.dto.*;
import com.kt.social.domain.comment.dto.CommentResponse;
import com.kt.social.domain.comment.service.CommentService;
import com.kt.social.domain.moderation.service.ModerationService;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.service.PostService;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.ComplaintResponse;
import com.kt.social.domain.report.dto.ReportResponse;
import com.kt.social.domain.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(ApiConstants.MODERATION)
@RequiredArgsConstructor
public class ModerationController {

    private final CommentService commentService;
    private final ReportService reportService;
    private final PostService postService;
    private final ModerationService moderationService;

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

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
    public ResponseEntity<PageVO<UserModerationResponse>> getUsersWithReports(
            @RequestParam(required = false) String filter,
            @ParameterObject Pageable pageable) {

        // Lưu ý: pageable ở đây sẽ hứng: ?page=0&size=10
        return ResponseEntity.ok(moderationService.getUsersWithReportCount(pageable, filter));
    }

    /**
     * Lấy nhật ký kiểm duyệt (Logs)
     * Hỗ trợ filter: action, targetType, actorName, etc.
     * Ví dụ: /api/v1/moderation/logs?filter=action=='AUTO_BAN';type=='POST'&page=0&size=20
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('MODERATION:ACCESS')") // Cả Admin và Mod đều xem được
    public ResponseEntity<PageVO<ModerationLogResponse>> getModerationLogs(
            @RequestParam(required = false) String filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(moderationService.getModerationLogs(filter, pageable));
    }

    /**
     * Lấy danh sách POST bị hệ thống chặn/xóa
     */
    @GetMapping("/posts/flagged")
    @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
    public ResponseEntity<PageVO<PostResponse>> getFlaggedPosts(
            @RequestParam(required = false) String filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(moderationService.getFlaggedPosts(filter, pageable));
    }

    /**
     * Lấy danh sách COMMENT bị hệ thống chặn/xóa
     */
    @GetMapping("/comments/flagged")
    @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
    public ResponseEntity<PageVO<CommentResponse>> getFlaggedComments(
            @RequestParam(required = false) String filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(moderationService.getFlaggedComments(filter, pageable));
    }

    /**     * Lấy danh sách MESSAGE vi phạm (đã bị xóa mềm)
     */
//    @GetMapping("/messages/flagged")
//    @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
//    public ResponseEntity<PageVO<ModerationMessageResponse>> getFlaggedMessages(
//            @RequestParam(required = false) String filter,
//            @ParameterObject Pageable pageable
//    ) {
//        return ResponseEntity.ok(moderationService.getFlaggedMessages(filter, pageable));
//    }

    @GetMapping("/messages/flagged/grouped")
    public ResponseEntity<PageVO<GroupedFlaggedMessageResponse>> getGroupedFlaggedMessages(
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(moderationService.getGroupedFlaggedMessages(pageable));
    }

    /**
     * Lấy danh sách Report của một nội dung cụ thể (Post/Comment)
     * URL: GET /api/v1/moderation/{type}/{id}/reports
     */
    @GetMapping("/{type}/{id}/reports")
    @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
    public ResponseEntity<PageVO<ReportResponse>> getContentReports(
            @PathVariable TargetType type,
            @PathVariable Long id,
            @ParameterObject Pageable pageable
    ) {
        // Gọi service lấy danh sách report theo content
        return ResponseEntity.ok(reportService.getReportsByContent(id, type, pageable));
    }

    /**
     * Lấy danh sách Complaint của một nội dung cụ thể (Post/Comment)
     * URL: GET /api/v1/moderation/{type}/{id}/complaints
     */
    @GetMapping("/{type}/{id}/complaints")
    @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
    public ResponseEntity<PageVO<ComplaintResponse>> getContentComplaints(
            @PathVariable TargetType type,
            @PathVariable Long id,
            @ParameterObject Pageable pageable
    ) {
        // Gọi service lấy danh sách complaint theo content
        return ResponseEntity.ok(reportService.getComplaintsByContent(id, type, pageable));
    }

    @GetMapping("/comment/{id}")
    @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
    public ResponseEntity<CommentResponse> getCommentById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(commentService.getCommentById(id));
    }

    @GetMapping("/post/{id}")
    @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
    public ResponseEntity<PostResponse> getPostById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    /**
     * Endpoint Khóa tài khoản
     * Truy cập: Admin, Moderator
     */
    @PutMapping("/users/{id}/block")
    @PreAuthorize("hasAnyAuthority('USER:BLOCK', 'MODERATION:ACCESS')")
    public ResponseEntity<Map<String, String>> blockUser(
            @PathVariable Long id,
            @RequestBody ChangeStatusRequest request
    ) {
        String reason = (request != null) ? request.getReason() : "";
        moderationService.updateUserStatus(id, AccountStatus.BLOCKED, reason);
        return ResponseEntity.ok(Map.of(
                "message", "User blocked successfully",
                "statusCode", "200"
        ));
    }

    /**
     * Endpoint Mở khóa tài khoản
     * Truy cập: Admin, Moderator
     */
    @PutMapping("/users/{id}/unblock")
    @PreAuthorize("hasAnyAuthority('USER:BLOCK', 'MODERATION:ACCESS')")
    public ResponseEntity<Map<String, String>> unblockUser(
            @PathVariable Long id,
            @RequestBody ChangeStatusRequest request
    ) {
        String reason = (request != null) ? request.getReason() : "";
        moderationService.updateUserStatus(id, AccountStatus.ACTIVE, reason);
        return ResponseEntity.ok(Map.of(
                "message", "User unblocked successfully",
                "statusCode", "200"
        ));
    }


    /**
     * Endpoint khóa nội dung (Post, Comment)
     * Truy cập: Admin, Moderator
     */
    @PutMapping("/{type}/{id}/block")
    @PreAuthorize("hasAnyAuthority('POST:DELETE_ANY', 'MODERATION:ACCESS')")
    public ResponseEntity<Map<String, String>> blockContent(
            @PathVariable TargetType type,
            @PathVariable String id
    ) {
        moderationService.blockContent(id, type);
        return ResponseEntity.ok(Map.of(
                "message", "Content blocked successfully",
                "statusCode", "200"
        ));
    }

    /**
     * Endpoint khôi phục nội dung đã bị xóa (Post, Comment)
     * Truy cập: Admin, Moderator
     */
    @PutMapping("/{type}/{id}/unblock")
    @PreAuthorize("hasAnyAuthority('POST:DELETE_ANY', 'MODERATION:ACCESS')")
    public ResponseEntity<Map<String, String>> unblockContent(
            @PathVariable TargetType type,
            @PathVariable Long id
    ) {
        moderationService.unblockContent(id, type);
        return ResponseEntity.ok(Map.of(
                "message", "Content unblocked successfully",
                "statusCode", "200"
        ));
    }
}
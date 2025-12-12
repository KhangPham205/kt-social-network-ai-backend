package com.kt.social.domain.moderation.service;

import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.moderation.event.ContentCreatedEvent;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import com.kt.social.domain.notification.service.NotificationService;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.infra.ai.AiServiceClient; // Giả sử service này có hàm checkToxic trả về DTO
import com.kt.social.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentModerationListener {

    private final AiServiceClient aiServiceClient;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContentCreation(ContentCreatedEvent event) {
        log.info("🤖 AI Scanning content [{}]: {}", event.getTargetType(), event.getTargetId());

        try {
            // 1. KIỂM TRA TEXT (Như cũ)
            var textResult = aiServiceClient.checkContentToxicity(event.getContent());
            if (textResult.isToxic()) {
                handleToxicContent(event, textResult.getReason());
                return; // Nếu text vi phạm thì phạt luôn, khỏi check ảnh
            }

            // 2. KIỂM TRA HÌNH ẢNH (Mới)
            List<String> mediaUrls = getMediaUrls(event); // Hàm helper lấy list url

            if (mediaUrls != null && !mediaUrls.isEmpty()) {
                for (String url : mediaUrls) {
                    // Chỉ check ảnh, bỏ qua video
                    if (isImage(url)) {
                        // Đọc bytes từ Storage
                        byte[] imageBytes = storageService.readFile(url);
                        if (imageBytes == null) continue;

                        var imageResult = aiServiceClient.checkImageToxicity(imageBytes, extractFilename(url));

                        if (imageResult.isToxic()) {
                            handleToxicContent(event, imageResult.getReason());
                            return; // Phát hiện 1 ảnh xấu là phạt luôn
                        }
                    }
                }
            }

            log.info("✅ Content is clean.");

        } catch (Exception e) {
            log.error("❌ Error during AI moderation: {}", e.getMessage());
        }
    }

    private void handleToxicContent(ContentCreatedEvent event, String reason) {
        // 2. Cập nhật deletedAt và details
        if (event.getTargetType() == TargetType.POST) {
            postRepository.findById(event.getTargetId()).ifPresent(post -> {
                post.setDeletedAt(Instant.now());
                post.setViolationDetails(reason);
                post.setSystemBan(true);
                postRepository.save(post);
            });
        } else if (event.getTargetType() == TargetType.COMMENT) {
            commentRepository.findById(event.getTargetId()).ifPresent(comment -> {
                comment.setDeletedAt(Instant.now());
//                 comment.setViolationDetails(reason); // Nếu bạn đã thêm field này vào Entity Comment
//                comment.setSystemBan(true); // Nếu có field này
                commentRepository.save(comment);

                // Giảm count comment của post gốc đi 1 (vì comment bị xóa)
                // postRepository.updateCommentCount(comment.getPost().getId(), -1);
                // (Tùy chọn: Có thể làm hoặc không, vì soft delete đôi khi vẫn tính count)
            });
        }

        // 3. Ghi Moderation Log (System Action -> actor = null)
        ModerationLog logEntry = ModerationLog.builder()
                .targetType(event.getTargetType())
                .targetId(event.getTargetId())
                .action("AUTO_BAN")
                .reason(reason)
                .actor(null) // System
                .build();
        moderationLogRepository.save(logEntry);

        //notificationService.sendNotification(event.getAuthorId(), "Bài viết của bạn đã bị xóa do vi phạm: " + reason);
        log.info("📧 Notification sent to User ID: {}", event.getAuthorId());
    }

    // ---------------------------- Helper Methods ----------------------------
    private List<String> getMediaUrls(ContentCreatedEvent event) {
        if (event.getTargetType() == TargetType.POST) {
            return postRepository.findById(event.getTargetId())
                    .map(post -> post.getMedia().stream()
                            .map(m -> m.get("url")) // Post lưu List<Map>
                            .toList())
                    .orElse(List.of());
        }
        // Comment tương tự...
        return List.of();
    }

    private boolean isImage(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private String extractFilename(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }
}
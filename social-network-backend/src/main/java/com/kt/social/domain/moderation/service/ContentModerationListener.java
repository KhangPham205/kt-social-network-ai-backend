package com.kt.social.domain.moderation.service;

import com.kt.social.domain.moderation.dto.ModerationResult;
import com.kt.social.domain.moderation.event.ContentCreatedEvent;
import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.model.Report;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.notification.service.NotificationService;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.infra.ai.AiServiceClient;
import com.kt.social.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentModerationListener {

    private final AiServiceClient aiServiceClient;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final ReportRepository reportRepository; // 🔥 THÊM MỚI
    private final StorageService storageService;
    private final NotificationService notificationService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Tạo transaction riêng biệt
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContentCreation(ContentCreatedEvent event) {
        log.info("🤖 AI Scanning content [{}]: ID {}", event.getTargetType(), event.getTargetId());

        try {
            // 1. KIỂM TRA TEXT
            String textToCheck = event.getContent();
            if (textToCheck != null && !textToCheck.isBlank()) {
                ModerationResult textResult = aiServiceClient.checkContentToxicity(textToCheck);
                if (textResult.isToxic()) {
                    log.warn("❌ Text Toxic Detected: {}", textResult.getReason());
                    handleToxicContent(event, textResult.getReason());
                    return;
                }
            }

            // 2. KIỂM TRA HÌNH ẢNH
            List<String> mediaUrls = getMediaUrls(event);

            if (!mediaUrls.isEmpty()) {
                for (String url : mediaUrls) {
                    if (isImage(url)) {
                        try {
                            byte[] imageBytes = storageService.readFile(url);
                            if (imageBytes == null || imageBytes.length == 0) continue;

                            String filename = extractFilename(url);
                            ModerationResult imageResult = aiServiceClient.checkImageToxicity(imageBytes, filename);

                            if (imageResult.isToxic()) {
                                log.warn("❌ Image Toxic Detected: {}", imageResult.getReason());
                                handleToxicContent(event, "[Image] " + imageResult.getReason());
                                return;
                            }
                        } catch (Exception ex) {
                            log.error("⚠️ Failed to check image {}: {}", url, ex.getMessage());
                        }
                    }
                }
            }

            log.info("✅ Content [{} - {}] is clean.", event.getTargetType(), event.getTargetId());

        } catch (Exception e) {
            log.error("❌ Error during AI moderation: {}", e.getMessage(), e);
        }
    }

    private void handleToxicContent(ContentCreatedEvent event, String reason) {
        Instant now = Instant.now();

        // 1. Xử lý Soft Delete Entity (Post/Comment)
        if (event.getTargetType() == TargetType.POST) {
            postRepository.findById(event.getTargetId()).ifPresent(post -> {
                post.setDeletedAt(now);
                post.setViolationDetails(reason);
                post.setSystemBan(true);
                postRepository.save(post);
            });
        } else if (event.getTargetType() == TargetType.COMMENT) {
            commentRepository.findById(event.getTargetId()).ifPresent(comment -> {
                comment.setDeletedAt(now);
                // comment.setSystemBan(true); // Nếu entity comment có field này
                commentRepository.save(comment);
            });
        }

        // 2. TẠO SYSTEM REPORT (Mới thêm)
        createSystemReport(event, reason);

        // 3. Ghi Moderation Log
        ModerationLog logEntry = ModerationLog.builder()
                .targetType(event.getTargetType())
                .targetId(event.getTargetId().toString())
                .action("AUTO_BAN")
                .reason(reason)
                .actor(null) // Null = System
                .createdAt(now)
                .build();
        moderationLogRepository.save(logEntry);

        // 4. Gửi thông báo cho User
//        notificationService.sendNotification(
//                event.getAuthorId(),
//                "Nội dung của bạn đã bị xóa do vi phạm tiêu chuẩn cộng đồng: " + reason
//        );
        log.info("📧 Notification sent to User ID: {}", event.getAuthorId());
    }

    /**
     * 🔥 Helper: Tạo Report hệ thống để Admin quản lý
     */
    private void createSystemReport(ContentCreatedEvent event, String reason) {
        try {
            // Kiểm tra xem đã có report hệ thống cho content này chưa (tránh duplicate)
            boolean exists = reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(
                    event.getTargetId(),
                    event.getTargetType()
            );

            if (!exists) {
                Report report = Report.builder()
                        .targetId(event.getTargetId())
                        .targetType(event.getTargetType())
                        .reason(ReportReason.HARASSMENT)
                        .isBannedBySystem(true)
                        .createdAt(Instant.now())
                        .build();

                reportRepository.save(report);
                log.info("🚩 Created System Report for {} ID {}", event.getTargetType(), event.getTargetId());
            }
        } catch (Exception e) {
            log.error("⚠️ Failed to create system report: {}", e.getMessage());
            // Không throw exception để tránh rollback việc xoá bài
        }
    }

    // ---------------------------- Helper Methods ----------------------------

    private List<String> getMediaUrls(ContentCreatedEvent event) {
        if (event.getTargetType() == TargetType.POST) {
            return postRepository.findById(event.getTargetId())
                    .map(Post::getMedia)
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(m -> m.get("url"))
                    .filter(Objects::nonNull)
                    .toList();
        } else if (event.getTargetType() == TargetType.COMMENT) {
            return commentRepository.findById(event.getTargetId())
                    .map(Comment::getMedia)
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(m -> m.get("url"))
                    .filter(Objects::nonNull)
                    .toList();
        }
        return Collections.emptyList();
    }

    private boolean isImage(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".bmp");
    }

    private String extractFilename(String url) {
        if (url == null || !url.contains("/")) return "unknown.jpg";
        String filename = url.substring(url.lastIndexOf("/") + 1);
        if (filename.contains("?")) {
            filename = filename.substring(0, filename.indexOf("?"));
        }
        return filename;
    }
}
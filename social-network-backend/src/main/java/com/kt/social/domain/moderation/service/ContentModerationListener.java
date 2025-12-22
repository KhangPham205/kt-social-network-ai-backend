package com.kt.social.domain.moderation.service;

import com.kt.social.domain.moderation.dto.ModerationResult;
import com.kt.social.domain.moderation.event.ContentCreatedEvent;
import com.kt.social.domain.moderation.event.MessageSentEvent;
import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.model.Report;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.infra.ai.AiServiceClient;
import com.kt.social.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentModerationListener {

    private final AiServiceClient aiServiceClient;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final ReportRepository reportRepository;
    private final StorageService storageService;
    private final ModerationService moderationService;

    // =================================================================================
    // 1. XỬ LÝ POST / COMMENT (Transactional)
    // =================================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContentCreation(ContentCreatedEvent event) {
        log.info("🤖 AI Scanning content [{}]: ID {}", event.getTargetType(), event.getTargetId());

        try {
            // A. Kiểm tra Text
            String textToCheck = event.getContent();
            if (textToCheck != null && !textToCheck.isBlank()) {
                ModerationResult textResult = aiServiceClient.checkContentToxicity(textToCheck);
                if (textResult.isToxic()) {
                    log.warn("❌ Text Toxic Detected: {}", textResult.getReason());
                    handleToxicPostOrComment(event, textResult.getReason());
                    return; // Nếu text vi phạm thì chặn luôn, không cần check ảnh
                }
            }

            // B. Kiểm tra Hình ảnh
            List<String> mediaUrls = getMediaUrls(event);
            if (!mediaUrls.isEmpty()) {
                for (String url : mediaUrls) {
                    if (isImage(url)) {
                        // Gọi hàm dùng chung checkSingleImage
                        ModerationResult imageResult = checkSingleImage(url);
                        if (imageResult != null && imageResult.isToxic()) {
                            log.warn("❌ Image Toxic Detected: {}", imageResult.getReason());
                            handleToxicPostOrComment(event, "[Image] " + imageResult.getReason());
                            return; // Chặn ngay khi thấy 1 ảnh vi phạm
                        }
                    }
                }
            }

            log.info("✅ Content [{} - {}] is clean.", event.getTargetType(), event.getTargetId());

        } catch (Exception e) {
            log.error("❌ Error during AI moderation: {}", e.getMessage(), e);
        }
    }

    // =================================================================================
    // 2. XỬ LÝ MESSAGE (Standard Event)
    // =================================================================================
    @Async
    @EventListener
    public void handleMessageSentEvent(MessageSentEvent event) {
        log.info("🤖 AI bắt đầu kiểm duyệt tin nhắn: {}", event.getId());
        String reason = null;
        boolean isToxic = false;

        try {
            // A. Kiểm tra Text
            if (event.getContent() != null && !event.getContent().isBlank()) {
                ModerationResult textResult = aiServiceClient.checkContentToxicity(event.getContent());
                if (textResult.isToxic()) {
                    isToxic = true;
                    reason = "[Text] " + textResult.getReason();
                }
            }

            // B. Kiểm tra Hình ảnh (Nếu text sạch và có media)
            if (!isToxic && event.getMedia() != null && !event.getMedia().isEmpty()) {
                for (Map<String, Object> mediaItem : event.getMedia()) {
                    String url = (String) mediaItem.get("url");

                    if (isImage(url)) {
                        ModerationResult imageResult = checkSingleImage(url);
                        if (imageResult != null && imageResult.isToxic()) {
                            isToxic = true;
                            reason = "[Image] " + imageResult.getReason();
                            break; // Dừng ngay khi phát hiện ảnh vi phạm
                        }
                    }
                }
            }

            // C. Xử lý kết quả
            if (isToxic) {
                log.warn("❌ Phát hiện vi phạm tin nhắn {}: {}", event.getId(), reason);

                // 1. Block tin nhắn
                moderationService.blockContent(event.getId(), TargetType.MESSAGE);

                // 2. Ghi Log (System Ban)
                saveModerationLog(TargetType.MESSAGE, event.getId(), reason);

                // 3. (Tùy chọn) Tạo Report nếu bảng Report hỗ trợ UUID
                // createSystemReportForMessage(event.getId(), reason);

            } else {
                log.info("✅ Tin nhắn an toàn: {}", event.getId());
            }

        } catch (Exception e) {
            log.error("⚠️ Lỗi khi kiểm duyệt tin nhắn {}: {}", event.getId(), e.getMessage());
        }
    }

    // =================================================================================
    // LOGIC XỬ LÝ VI PHẠM & HELPER
    // =================================================================================

    /**
     * Xử lý khi Post/Comment bị AI đánh dấu là độc hại
     */
    private void handleToxicPostOrComment(ContentCreatedEvent event, String reason) {
        Instant now = Instant.now();
        Long authorId = null;

        // 1. Soft Delete Entity & Lấy ID tác giả
        if (event.getTargetType() == TargetType.POST) {
            Post post = postRepository.findById(event.getTargetId()).orElse(null);
            if (post != null) {
                post.setDeletedAt(now);
                post.setViolationDetails(reason);
                post.setSystemBan(true);
                postRepository.save(post);
                if (post.getAuthor() != null) authorId = post.getAuthor().getId();
            }
        } else if (event.getTargetType() == TargetType.COMMENT) {
            Comment comment = commentRepository.findById(event.getTargetId()).orElse(null);
            if (comment != null) {
                comment.setDeletedAt(now);
                commentRepository.save(comment);
                // Lưu ý: Kiểm tra lại tên getter trong entity Comment (getAuthor hay getUser)
                if (comment.getAuthor() != null) authorId = comment.getAuthor().getId();
            }
        }

        // 2. Tạo Report (Chỉ tạo khi lấy được Author ID để tránh lỗi null DB)
        if (authorId != null) {
            createSystemReportForPostOrComment(event.getTargetId(), event.getTargetType(), authorId, reason);
        }

        // 3. Ghi Log
        saveModerationLog(event.getTargetType(), event.getTargetId().toString(), reason);
        log.info("🚫 Auto-banned {} ID: {}", event.getTargetType(), event.getTargetId());
    }

    /**
     * Hàm dùng chung: Đọc file từ Storage và gửi sang AI Service
     */
    private ModerationResult checkSingleImage(String url) {
        try {
            // Đọc bytes từ StorageService
            byte[] imageBytes = storageService.readFile(url);

            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("⚠️ Không thể đọc file ảnh hoặc file rỗng: {}", url);
                return null;
            }

            String filename = extractFilename(url);
            // Gửi sang AI
            return aiServiceClient.checkImageToxicity(imageBytes, filename);
        } catch (Exception e) {
            log.error("⚠️ Lỗi khi check ảnh {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Lưu Report vào DB (Fix lỗi null target_user_id)
     */
    private void createSystemReportForPostOrComment(Long targetId, TargetType type, Long targetUserId, String reason) {
        try {
            boolean exists = reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(targetId.toString(), type);
            if (!exists) {
                Report report = Report.builder()
                        .targetId(targetId.toString())
                        .targetType(type)
                        .targetUserId(targetUserId) // ✅ Đã fix: Truyền ID người bị report
                        .reason(ReportReason.HARASSMENT)
                        .customReason(reason)
                        .isBannedBySystem(true)
                        .status(ReportStatus.PENDING)
                        .createdAt(Instant.now())
                        .build();
                reportRepository.save(report);
            }
        } catch (Exception e) {
            log.error("⚠️ Failed to create system report: {}", e.getMessage());
        }
    }

    private void saveModerationLog(TargetType type, String targetId, String reason) {
        ModerationLog logEntry = ModerationLog.builder()
                .targetType(type)
                .targetId(targetId)
                .action("AUTO_BAN")
                .reason(reason)
                .actor(null) // Null = System
                .createdAt(Instant.now())
                .build();
        moderationLogRepository.save(logEntry);
    }

    // ---------------------------- Utils ----------------------------

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
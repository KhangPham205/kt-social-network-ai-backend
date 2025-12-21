package com.kt.social.domain.moderation.service;

import com.kt.social.domain.moderation.dto.ModerationResult;
import com.kt.social.domain.moderation.event.ContentCreatedEvent;
import com.kt.social.domain.moderation.event.MessageSentEvent;
import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ReportReason;
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

    /**
     * 1. XỬ LÝ POST / COMMENT (Transactional Event)
     * Chạy sau khi transaction commit thành công.
     */
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
                    return;
                }
            }

            // B. Kiểm tra Hình ảnh
            List<String> mediaUrls = getMediaUrls(event);
            if (!mediaUrls.isEmpty()) {
                for (String url : mediaUrls) {
                    if (isImage(url)) {
                        checkImageContent(event, url);
                    }
                }
            }

            log.info("✅ Content [{} - {}] is clean.", event.getTargetType(), event.getTargetId());

        } catch (Exception e) {
            log.error("❌ Error during AI moderation: {}", e.getMessage(), e);
        }
    }

    /**
     * 2. XỬ LÝ MESSAGE (Standard Event)
     * Chạy bất đồng bộ, độc lập với transaction gửi tin nhắn.
     */
    @Async
    @EventListener
    public void handleMessageSentEvent(MessageSentEvent event) {
        log.info("🤖 AI bắt đầu kiểm duyệt tin nhắn: {}", event.getId());

        try {
            // 1. Gọi AI Service để check text
            ModerationResult result = aiServiceClient.checkContentToxicity(event.getContent());

            // 2. Nếu phát hiện vi phạm
            if (result.isToxic()) {
                log.warn("❌ Phát hiện vi phạm tin nhắn {}: {}", event.getId(), result.getReason());

                // 3. Gọi ModerationService để Block tin nhắn
                // Lưu ý: Hàm blockContent đã được sửa để nhận String ID
                moderationService.blockContent(event.getId(), TargetType.MESSAGE);

                // 4. Tạo System Report (Log vào bảng Report nếu cần)
                // Lưu ý: Chỉ tạo được nếu bảng Report hỗ trợ lưu ID dạng String (UUID)
                createSystemReportForMessage(event.getId(), result.getReason());
            } else {
                log.info("✅ Tin nhắn an toàn: {}", event.getId());
            }
        } catch (Exception e) {
            log.error("Lỗi khi kiểm duyệt tin nhắn {}: {}", event.getId(), e.getMessage());
        }
    }

    // ---------------------------- Logic Xử Lý Vi Phạm ----------------------------

    private void handleToxicPostOrComment(ContentCreatedEvent event, String reason) {
        Instant now = Instant.now();

        // 1. Soft Delete Entity
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
                commentRepository.save(comment);
            });
        }

        // 2. Tạo Report Hệ Thống
        createSystemReportForPostOrComment(event.getTargetId(), event.getTargetType(), reason);

        // 3. Ghi Moderation Log
        saveModerationLog(event.getTargetType(), event.getTargetId().toString(), reason);

        log.info("🚫 Auto-banned {} ID: {}", event.getTargetType(), event.getTargetId());
    }

    private void checkImageContent(ContentCreatedEvent event, String url) {
        try {
            byte[] imageBytes = storageService.readFile(url);
            if (imageBytes == null || imageBytes.length == 0) return;

            String filename = extractFilename(url);
            ModerationResult imageResult = aiServiceClient.checkImageToxicity(imageBytes, filename);

            if (imageResult.isToxic()) {
                log.warn("❌ Image Toxic Detected: {}", imageResult.getReason());
                handleToxicPostOrComment(event, "[Image] " + imageResult.getReason());
            }
        } catch (Exception ex) {
            log.error("⚠️ Failed to check image {}: {}", url, ex.getMessage());
        }
    }

    // ---------------------------- Helper Tạo Report & Log ----------------------------

    /**
     * Tạo Report cho Post/Comment (ID là Long)
     */
    private void createSystemReportForPostOrComment(Long targetId, TargetType type, String reason) {
        try {
            boolean exists = reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(targetId, type);
            if (!exists) {
                Report report = Report.builder()
                        .targetId(targetId)
                        .targetType(type)
                        .reason(ReportReason.HARASSMENT)
                        .isBannedBySystem(true)
                        .createdAt(Instant.now())
                        .build();
                reportRepository.save(report);
            }
        } catch (Exception e) {
            log.error("⚠️ Failed to create system report for Post/Comment: {}", e.getMessage());
        }
    }

    /**
     * Tạo Report cho Message (ID là String UUID)
     */
    private void createSystemReportForMessage(String messageIdStr, String reason) {
        try {
            // Giả sử bạn ĐÃ migrate bảng Report cột target_id sang String/Varchar
            // Nếu chưa migrate, bạn chỉ nên ghi ModerationLog (đã xử lý ở hàm blockContent)

            // Uncomment dòng dưới nếu bảng Report hỗ trợ String ID
//             long count = reportRepository.countByTargetTypeAndTargetId(TargetType.MESSAGE, messageIdStr); // Cần repo hỗ trợ String
//             if (count == 0) {
//                 Report report = Report.builder()
//                         .targetType(TargetType.MESSAGE)
//                         .targetId(messageIdStr) // Cần sửa entity Report field targetId thành String
//                         .reason(ReportReason.HARASSMENT)
//                         .isBannedBySystem(true)
//                         .createdAt(Instant.now())
//                         .build();
//                 reportRepository.save(report);
//             }

            // Thay vào đó, ta ghi Log (ModerationLog đã hỗ trợ String ID do bước trước ta làm)
            saveModerationLog(TargetType.MESSAGE, messageIdStr, reason);

        } catch (Exception e) {
            log.error("⚠️ Failed to create system report for Message: {}", e.getMessage());
        }
    }

    private void saveModerationLog(TargetType type, String targetId, String reason) {
        ModerationLog logEntry = ModerationLog.builder()
                .targetType(type)
                .targetId(targetId) // Field này phải là String trong Entity
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
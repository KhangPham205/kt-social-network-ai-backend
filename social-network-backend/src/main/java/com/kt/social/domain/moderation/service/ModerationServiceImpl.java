package com.kt.social.domain.moderation.service;

import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.admin.dto.ModerationMessageResponse;
import com.kt.social.domain.admin.dto.ModerationUserDetailResponse;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.repository.ConversationRepository;
import com.kt.social.domain.moderation.dto.ModerationLogResponse;
import com.kt.social.domain.moderation.dto.UserModerationResponse;
import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.ReportResponse;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.mapper.ReportMapper;
import com.kt.social.domain.report.model.Report;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;
    private final ModerationLogRepository moderationLogRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public ModerationUserDetailResponse getUserDetailForAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Tính số lần User này bị report và đã duyệt (TargetType = USER)
        // Lưu ý: Nếu muốn tính tổng cả việc Post/Comment của user bị report thì query sẽ phức tạp hơn.
        // Ở đây tạm tính số lần Profile bị report.
        long violations = reportRepository.countByTargetTypeAndTargetIdAndStatus(
                TargetType.USER,
                userId,
                ReportStatus.APPROVED
        );

        return ModerationUserDetailResponse.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .email(user.getCredential().getEmail()) // Lấy từ UserCredential
                .status(user.getCredential().getStatus()) // Lấy status
                .bio(user.getUserInfo() != null ? user.getUserInfo().getBio() : null)
                .violationCount(violations)
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationMessageResponse getMessageDetailForAdmin(String messageId) {
        // 1. Tìm Conversation chứa message này
        Conversation conversation = conversationRepository.findByMessageIdInJson(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found in any conversation"));

        // 2. Lọc trong List<Map> để lấy đúng message object
        Map<String, Object> messageData = conversation.getMessages().stream()
                .filter(msg -> Objects.equals(String.valueOf(msg.get("id")), messageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Message data is missing"));

        // 3. Lấy thông tin Sender (từ senderId trong JSON)
        // Lưu ý: JSON số thường được parse thành Integer hoặc Long, cần ép kiểu an toàn
        Long senderId = Long.valueOf(String.valueOf(messageData.get("senderId")));

        User sender = userRepository.findById(senderId)
                .orElse(User.builder()
                        .id(senderId)
                        .displayName("Unknown User")
                        .avatarUrl(null)
                        .build()); // Fallback nếu user đã bị xóa cứng

        // 4. Xử lý Media (Trích xuất URL từ JSON)
        Object mediaObj = messageData.get("media");
        List<String> mediaUrls = new ArrayList<>();

        if (mediaObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map) {
                    // Trường hợp lưu dạng: [{"url": "http...", "type": "image"}]
                    Map<?, ?> map = (Map<?, ?>) item;
                    Object url = map.get("url");
                    if (url != null) {
                        mediaUrls.add(String.valueOf(url));
                    }
                } else if (item instanceof String) {
                    // Trường hợp lưu dạng: ["http...", "http..."]
                    mediaUrls.add((String) item);
                }
            }
        }

        // 5. Map sang DTO
        return ModerationMessageResponse.builder()
                .id(messageId)
                .conversationId(conversation.getId())
                .senderId(senderId)
                .senderName(sender.getDisplayName())
                .senderAvatar(sender.getAvatarUrl())
                .content((String) messageData.get("content"))
                .sentAt(String.valueOf(messageData.get("timestamp")))
                .mediaUrls(mediaUrls)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        Page<Report> page = reportRepository.findAllViolationsByUserId(
                userId,
                ReportStatus.APPROVED,
                pageable
        );

        List<ReportResponse> content = page.getContent().stream()
                .map(reportMapper::toResponse)
                .toList();

        return PageVO.<ReportResponse>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserModerationResponse> getUsersWithReportCount(int page, int size) {
        // Sắp xếp mặc định: Người bị report nhiều nhất lên đầu
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reportCount"));

        // Lưu ý: Sort trong PageRequest với custom query 'new DTO...' đôi khi phức tạp.
        // Nếu Sort trên bị lỗi, hãy đổi thành Sort.unsorted() và handle sort trong Query hoặc Frontend.
        // Cách an toàn nhất là để sort tại Frontend hoặc Query cứng "ORDER BY COUNT(r) DESC".

        return userRepository.findAllUsersWithReportCount(PageRequest.of(page, size));
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable) {
        Specification<ModerationLog> spec = Specification.where(null);

        if (filter != null && !filter.isBlank()) {
            // Mapping các alias để filter dễ hơn
            Map<String, String> propertyPathMapper = new HashMap<>();
            propertyPathMapper.put("actorId", "actor.id");
            propertyPathMapper.put("actorName", "actor.displayName");
            propertyPathMapper.put("type", "targetType"); // filter=type=='POST'

            spec = RSQLJPASupport.toSpecification(filter, propertyPathMapper);
        }

        Page<ModerationLog> page = moderationLogRepository.findAll(spec, pageable);

        List<ModerationLogResponse> content = page.getContent().stream()
                .map(this::mapLogToResponse)
                .toList();

        return PageVO.<ModerationLogResponse>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    @Override
    @Transactional
    public void restoreContent(Long id, TargetType targetType) {
        User admin = userService.getCurrentUser();

        // 1. Khôi phục nội dung (Post/Comment)
        if (targetType == TargetType.POST) {
            // Lưu ý: Cần dùng hàm find riêng để tìm được cả bài đã bị soft-delete
            Post post = postRepository.findByIdIncludingDeleted(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

            post.setDeletedAt(null);
            post.setSystemBan(false);
            post.setViolationDetails(null); // Xóa lý do vi phạm cũ (tuỳ chọn)
            postRepository.save(post);

        } else if (targetType == TargetType.COMMENT) {
            Comment comment = commentRepository.findByIdIncludingDeleted(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

            comment.setDeletedAt(null);
            // comment.setSystemBan(false); // Nếu comment có field này
            commentRepository.save(comment);
        }

        // Tìm tất cả các report ĐÃ DUYỆT (APPROVED) liên quan đến nội dung này
        List<Report> relatedReports = reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
                targetType, id, ReportStatus.APPROVED
        );

        if (!relatedReports.isEmpty()) {
            for (Report report : relatedReports) {
                // Ghi lại lịch sử thay đổi của Report
                report.getHistory().add(Report.ReportHistory.builder()
                        .actorId(admin.getId())
                        .actorName(admin.getDisplayName())
                        .oldStatus(ReportStatus.APPROVED)
                        .newStatus(ReportStatus.REJECTED)
                        .note("System: Tự động từ chối do Admin đã khôi phục nội dung gốc.")
                        .timestamp(Instant.now())
                        .build());

                // Đổi trạng thái thành REJECTED (Coi như báo cáo sai/không còn hiệu lực)
                report.setStatus(ReportStatus.REJECTED);
            }
            reportRepository.saveAll(relatedReports);
        }

        // 3. Ghi Log Moderation (Admin Action)
        moderationLogRepository.save(ModerationLog.builder()
                .targetType(targetType)
                .targetId(id)
                .action("ADMIN_RESTORE")
                .actor(admin)
                .reason("Admin restored content manually")
                .createdAt(Instant.now())
                .build());
    }

    // Helper map entity -> dto
    private ModerationLogResponse mapLogToResponse(ModerationLog log) {
        return ModerationLogResponse.builder()
                .id(log.getId())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .action(log.getAction())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                // Xử lý Actor: Nếu actor null nghĩa là System/AI thực hiện
                .actorId(log.getActor() != null ? log.getActor().getId() : null)
                .actorName(log.getActor() != null ? log.getActor().getDisplayName() : "System (AI)")
                .actorAvatar(log.getActor() != null ? log.getActor().getAvatarUrl() : null)
                .build();
    }
}

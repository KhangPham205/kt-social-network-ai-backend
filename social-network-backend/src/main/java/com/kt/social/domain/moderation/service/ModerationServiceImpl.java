package com.kt.social.domain.moderation.service;

import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.admin.dto.ModerationLogResponse;
import com.kt.social.domain.admin.dto.ModerationMessageResponse;
import com.kt.social.domain.admin.dto.ModerationUserDetailResponse;
import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.repository.ConversationRepository;
import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.ReportResponse;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.mapper.ReportMapper;
import com.kt.social.domain.report.model.Report;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;
    private final ModerationLogRepository moderationLogRepository;

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

        // 4. Map sang DTO
        return ModerationMessageResponse.builder()
                .id(messageId)
                .conversationId(conversation.getId())
                .senderId(senderId)
                .senderName(sender.getDisplayName())
                .senderAvatar(sender.getAvatarUrl())
                .content((String) messageData.get("content"))
                .sentAt(String.valueOf(messageData.get("timestamp")))
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

package com.kt.social.domain.moderation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.common.dto.IdCount;
import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.moderation.dto.*;
import com.kt.social.domain.audit.service.ActivityLogService;
import com.kt.social.domain.comment.dto.CommentResponse;
import com.kt.social.domain.comment.mapper.CommentMapper;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.repository.ConversationRepository;
import com.kt.social.domain.message.service.MessageService;
import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.moderation.repository.ModerationLogRepository;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.mapper.PostMapper;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.dto.ReportResponse;
import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.mapper.ReportMapper;
import com.kt.social.domain.report.model.Report;
import com.kt.social.domain.report.repository.ComplaintRepository;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.ai.AiServiceClient;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    @Autowired
    private ObjectMapper objectMapper;

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final ActivityLogService activityLogService;
    private final MessageService messageService;
    private final ConversationRepository conversationRepository;
    private final ReportRepository reportRepository;
    private final ComplaintRepository complaintRepository;
    private final ReportMapper reportMapper;
    private final ModerationLogRepository moderationLogRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final AiServiceClient aiServiceClient;

    @Override
    @Transactional(readOnly = true)
    public ModerationUserDetailResponse getUserDetailForAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 🔥 SỬA: Đếm tổng số Report nhắm vào user này (targetUserId)
        // Không còn check ReportStatus.APPROVED nữa
        long totalReports = reportRepository.countByTargetUserId(userId);

        return ModerationUserDetailResponse.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .email(user.getCredential().getEmail())
                .status(user.getCredential().getStatus())
                .bio(user.getUserInfo() != null ? user.getUserInfo().getBio() : null)
                .violationCount(totalReports) // Trả về tổng số lần bị báo cáo
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        // 🔥 SỬA: Lấy tất cả report nhắm vào user này
        Page<Report> page = reportRepository.findByTargetUserId(userId, pageable);

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
    public ModerationMessageResponse getMessageDetailForAdmin(String messageId) {
        // 1. Tìm Conversation chứa message này (Query JSONB)
        Conversation conversation = conversationRepository.findByMessageIdInJson(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found in any conversation"));

        // 2. Lọc trong List<Map> JSON để lấy đúng message object
        Map<String, Object> messageData = conversation.getMessages().stream()
                .filter(msg -> Objects.equals(String.valueOf(msg.get("id")), messageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Message data is missing"));

        // 3. Lấy thông tin Sender
        // Ưu tiên lấy từ DB để có info mới nhất, nếu user bị xóa thì fallback về data trong JSON
        Long senderId = parseLongSafely(messageData.get("senderId"));
        User sender = userRepository.findById(senderId)
                .orElse(null);

        String senderName = sender != null ? sender.getDisplayName() : (String) messageData.get("senderName");
        String senderAvatar = sender != null ? sender.getAvatarUrl() : (String) messageData.get("senderAvatar");

        // 4. Xử lý Media (Giữ nguyên cấu trúc Map để có cả URL và Type)
        List<Map<String, Object>> mediaList = new ArrayList<>();
        Object mediaObj = messageData.get("media");

        if (mediaObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawMap) {
                    try {
                        Map<String, Object> safeMap = new HashMap<>();
                        safeMap.put("url", String.valueOf(rawMap.get("url")));
                        Object typeObj = rawMap.get("type");
                        safeMap.put("type", typeObj != null ? String.valueOf(typeObj) : "file");
                        mediaList.add(safeMap);
                    } catch (Exception e) {
                        log.info(e.getMessage());
                    }
                }
            }
        }

        // 5. Xử lý thời gian (createdAt & deletedAt)
        // Lưu ý: Key trong JSON là "createdAt", không phải "timestamp"
        String sentAtStr = String.valueOf(messageData.get("createdAt"));

        Instant deletedAt = null;
        Object deletedAtObj = messageData.get("deletedAt");
        if (deletedAtObj != null) {
            try {
                deletedAt = Instant.parse(String.valueOf(deletedAtObj));
            } catch (Exception ignored) {}
        }

        // 6. Lấy số lượng Report & Complaint
        // Lưu ý: Nếu ID là UUID String và DB Report dùng Long, đoạn này cần xử lý riêng.
        // Giả sử ReportRepository hỗ trợ đếm theo String ID hoặc bạn đã convert.
        long reportCount = 0;
        long complaintCount = 0;
//        try {
//            // Nếu bạn lưu targetId trong bảng Report là String -> Dùng thẳng messageId
//            // Nếu lưu là Long -> Cần hash hoặc logic khác. Ở đây giả định bạn xử lý được việc map ID.
//            // reportCount = reportRepository.countByTargetTypeAndTargetId(TargetType.MESSAGE, messageId);
//        } catch (Exception e) {
//            // Log error
//        }

        // 7. Map sang DTO
        return ModerationMessageResponse.builder()
                .id(messageId)
                .conversationId(conversation.getId())
                .senderId(senderId)
                .senderName(senderName)
                .senderAvatar(senderAvatar)
                .content((String) messageData.get("content"))
                .sentAt(sentAtStr)
                .media(mediaList)
                .deletedAt(deletedAt) // 🔥 Map thêm deletedAt
                .reportCount(reportCount)
                .complaintCount(complaintCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserModerationResponse> getUsersWithReportCount(Pageable pageable, String filter) {
        String keyword = null;
        if (filter != null && !filter.isBlank()) {
            if (filter.contains("=='")) {
                keyword = filter.split("=='")[1].replace("'", "").trim();
            } else {
                keyword = filter;
            }
        }

        Pageable newPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<UserModerationResponse> page = userRepository.findAllUsersWithReportCount(keyword, newPageable);

        return PageVO.<UserModerationResponse>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(page.getNumberOfElements())
                .content(page.getContent())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getFlaggedPosts(String filter, Pageable pageable) {
        String keyword = extractKeyword(filter);

        Pageable sqlPageable = mapNativeQueryPageable(pageable, "POST");

        Page<Post> page = postRepository.findAllFlaggedPosts(keyword, sqlPageable);

        List<PostResponse> content = page.getContent().stream()
                .map(postMapper::toDto)
                .toList();

        enrichWithCounts(content, PostResponse::getId, PostResponse::setReportCount, PostResponse::setComplaintCount, TargetType.POST);

        return buildPageVO(page, content);
    }

    // --- 2. COMMENT ---
    @Override
    @Transactional(readOnly = true)
    public PageVO<CommentResponse> getFlaggedComments(String filter, Pageable pageable) {
        String keyword = extractKeyword(filter);

        Pageable sqlPageable = mapNativeQueryPageable(pageable, "COMMENT");

        Page<Comment> page = commentRepository.findAllFlaggedComments(keyword, sqlPageable);

        List<CommentResponse> content = page.getContent().stream()
                .map(commentMapper::toDto)
                .toList();

        enrichWithCounts(content, CommentResponse::getId, CommentResponse::setReportCount, CommentResponse::setComplaintCount, TargetType.COMMENT);

        return buildPageVO(page, content);
    }

    // --- 3. MESSAGE ---
    @Override
    @Transactional(readOnly = true)
    public PageVO<ModerationMessageResponse> getFlaggedMessages(String filter, Pageable pageable) {
        Pageable sqlPageable = mapNativeQueryPageable(pageable, "MESSAGE");

        Page<FlaggedMessageProjection> page = conversationRepository.findFlaggedMessages(filter, sqlPageable);

        List<ModerationMessageResponse> content = page.getContent().stream()
                .map(p -> {
                    // 1. Parse Media
                    List<Map<String, Object>> mediaList = new ArrayList<>();
                    String mediaJson = p.getMedia();
                    if (mediaJson != null && !mediaJson.isEmpty() && !"null".equals(mediaJson)) {
                        try {
                            mediaList = objectMapper.readValue(mediaJson, new TypeReference<List<Map<String, Object>>>() {});
                        } catch (JsonProcessingException e) {
                            log.error("Error parsing media JSON for message {}: {}", p.getId(), e.getMessage());
                        }
                    }

                    // 2. Parse Date (String -> Instant)
                    // Postgres JSON trả về String dạng ISO-8601 (ví dụ: "2023-10-25T10:00:00Z")
                    Instant sentAtInstant = null;
                    if (p.getSentAt() != null) {
                        try {
                            // Thường chuỗi trong JSONB sẽ có quote bao quanh (VD: "2023..."), cần check kỹ
                            String rawDate = p.getSentAt().replace("\"", "");
                            sentAtInstant = Instant.parse(rawDate);
                        } catch (DateTimeParseException e) {
                            log.warn("Cannot parse sentAt: {}", p.getSentAt());
                        }
                    }

                    return ModerationMessageResponse.builder()
                            .id(p.getId())
                            .conversationId(p.getConversationId())
                            .senderId(p.getSenderId())
                            .senderName(p.getSenderName())
                            .senderAvatar(p.getSenderAvatar())
                            .content(p.getContent())
                            .sentAt(String.valueOf(sentAtInstant))
                            .deletedAt(p.getDeletedAt())
                            .media(mediaList)
                            .build();
                })
                .collect(Collectors.toList());

        try {
            enrichWithCounts(content,
                    ModerationMessageResponse::getId,
                    ModerationMessageResponse::setReportCount,
                    ModerationMessageResponse::setComplaintCount,
                    TargetType.MESSAGE);
        } catch (Exception e) {
            log.error("Error enriching counts for messages: {}", e.getMessage());
        }

        return buildPageVO(page, content);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<GroupedFlaggedMessageResponse> getGroupedFlaggedMessages(Pageable pageable) {
        // 1. Lấy danh sách Conversation có vấn đề (Query trong Repo đã check: Deleted OR Exists in Report)
        Page<Conversation> page = conversationRepository.findConversationsWithFlaggedMessages(pageable);

        // 2. Gom tất cả Message ID trong trang hiện tại để query bảng Report 1 lần (Batch Query - Tránh N+1)
        List<String> allMessageIds = page.getContent().stream()
                .flatMap(c -> {
                    if (c.getMessages() == null) return Stream.empty();
                    return c.getMessages().stream()
                            .map(m -> (String) m.get("id"))
                            .filter(Objects::nonNull);
                })
                .toList();

        // 3. Tìm những ID nào thực sự đang bị Report (để dùng cho logic filter bên dưới)
        Set<String> reportedMessageIds;
        if (allMessageIds.isEmpty()) {
            reportedMessageIds = Collections.emptySet();
        } else {
            reportedMessageIds = reportRepository.findReportedTargetIds(TargetType.MESSAGE, allMessageIds);
        }

        // 4. Map và Filter (Logic chính nằm ở mapToGroupedResponse)
        List<GroupedFlaggedMessageResponse> content = page.getContent().stream()
                .map(c -> mapToGroupedResponse(c, reportedMessageIds))
                // (Tùy chọn) Chỉ lấy những conversation còn tin nhắn sau khi filter
                .filter(g -> !g.getFlaggedMessages().isEmpty())
                .collect(Collectors.toList());

        return PageVO.<GroupedFlaggedMessageResponse>builder()
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

    @Override
    @Transactional(readOnly = true)
    public PageVO<ModerationLogResponse> getHistory(TargetType type, String id, Pageable pageable, String filter) {

        // 1. Gọi Repository
        Page<ModerationLog> page = moderationLogRepository.findHistory(type, id, filter, pageable);

        // 2. Map sang DTO
        List<ModerationLogResponse> content = page.getContent().stream()
                .map(this::mapToDto)
                .toList();

        // 3. Trả về PageVO (bao gồm content + page, size, totalElements, totalPages)
        return buildPageVO(page, content);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long targetUserId, AccountStatus newStatus, String reason) {
        User currentUser = userService.getCurrentUser();

        // 1. Kiểm tra User tồn tại
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 2. Validate: Không được tự khóa chính mình
        if (currentUser.getId().equals(targetUserId)) {
            throw new BadRequestException("Bạn không thể tự khóa/mở khóa tài khoản của chính mình.");
        }

        // 3. Validate: Moderator không được khóa Admin (Logic phân quyền cơ bản)
        boolean isActorAdmin = currentUser.getCredential().getRoles().stream()
                .anyMatch(r -> r.getName().equals("ADMIN"));
        boolean isTargetAdmin = targetUser.getCredential().getRoles().stream()
                .anyMatch(r -> r.getName().equals("ADMIN"));

        if (isTargetAdmin && !isActorAdmin) {
            throw new AccessDeniedException("Moderator không có quyền khóa tài khoản Admin.");
        }

        // 4. Cập nhật trạng thái trong UserCredential
        UserCredential credential = targetUser.getCredential();
        credential.setStatus(newStatus);

        userCredentialRepository.save(credential);
        // userRepository.save(targetUser); // Nếu có thay đổi ở bảng User

        // 5. Ghi Log hành động
        activityLogService.logActivity(
                currentUser,
                newStatus == AccountStatus.BLOCKED ? "USER:BLOCK_ACCOUNT" : "USER:UNBLOCK_ACCOUNT",
                "User",
                targetUserId,
                Map.of("reason", reason != null ? reason : "No reason provided",
                        "newStatus", newStatus.toString())
        );
    }

    @Override
    @Transactional
    public void blockContent(String idStr, TargetType targetType) { // Nhận String trực tiếp
        User actor = null;
        try {
            // Chỉ lấy user nếu đang trong ngữ cảnh request HTTP thông thường
            if (SecurityContextHolder.getContext().getAuthentication() != null
                    && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                    && !"anonymousUser".equals(SecurityContextHolder.getContext().getAuthentication().getPrincipal())) {
                actor = userService.getCurrentUser();
            }
        } catch (Exception e) {
            // Bỏ qua lỗi Authentication missing, coi như System đang thực hiện
            log.info("Block content triggered by System (Async/AI)");
        }

        try {
            if (targetType == TargetType.POST) {
                Long postId = Long.parseLong(idStr); // Parse Long ở đây
                Post post = postRepository.findById(postId)
                        .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

                post.setDeletedAt(Instant.now());
                post.setSystemBan(true);
                postRepository.save(post);

            } else if (targetType == TargetType.COMMENT) {
                Long commentId = Long.parseLong(idStr); // Parse Long ở đây
                Comment comment = commentRepository.findById(commentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

                comment.setDeletedAt(Instant.now());
                commentRepository.save(comment);

            } else if (targetType == TargetType.MESSAGE) {
                // ID là String UUID, truyền thẳng
                messageService.softDeleteMessage(idStr);
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("ID không hợp lệ cho loại " + targetType);
        }

        saveLog(actor, targetType, idStr, "BLOCK", "Admin blocked content");
    }

    @Override
    @Transactional
    public void unblockContent(Long id, TargetType targetType) {
        User admin = userService.getCurrentUser();

        if (targetType == TargetType.POST) {
            // Dùng findByIdIncludingDeleted đã viết ở bước trước để tìm bài bị xóa
            Post post = postRepository.findByIdIncludingDeleted(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

            post.setDeletedAt(null);
            post.setSystemBan(false);
            post.setViolationDetails(null);
            postRepository.save(post);

        } else if (targetType == TargetType.COMMENT) {
            Comment comment = commentRepository.findByIdIncludingDeleted(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

            comment.setDeletedAt(null);
            commentRepository.save(comment);
        }

        // Ghi Log hành động
        saveLog(admin, targetType, String.valueOf(id), "UNBLOCK", "Admin restored content");
    }

    @Override
    public void validatePostContent(Post post) {
        // 1. Gọi AI kiểm tra
        ModerationResult result = aiServiceClient.checkContentToxicity(post.getContent());

        // 2. Nếu độc hại -> Tạo Report tự động
        if (result.isToxic()) {
            createSystemAutoReport(
                    post.getId(),
                    TargetType.POST,
                    result.getReason()
            );
        }
    }

    @Override
    public void validateImage(Long mediaId, byte[] imageBytes, String filename) {
        ModerationResult result = aiServiceClient.checkImageToxicity(imageBytes, filename);

        if (result.isToxic()) {
            createSystemAutoReport(
                    mediaId,
                    TargetType.MEDIA,
                    result.getReason()
            );
        }
    }

    private void createSystemAutoReport(Long targetId, TargetType targetType, String aiReason) {
        try {
            boolean exists = reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(targetId.toString(), targetType);
            if (exists) return;

//            String systemNote = String.format("[SYSTEM AI DETECTED] Hệ thống tự động chặn. Lý do chi tiết: %s", aiReason);

            Report report = Report.builder()
                    .targetId(targetId.toString())
                    .targetType(targetType)
                    .reporter(null)
                    .reason(ReportReason.HARASSMENT)
                    .isBannedBySystem(true)
                    .createdAt(Instant.now())
                    .build();

            reportRepository.save(report);
            log.info("🤖 Đã tạo System Report cho {} ID: {}", targetType, targetId);

        } catch (Exception e) {
            log.error("Lỗi khi tạo System Report: {}", e.getMessage());
        }
    }

    // --- Helper ghi log ---
    private void saveLog(User actor, TargetType type, String targetId, String action, String reason) {
        moderationLogRepository.save(ModerationLog.builder()
                .actor(actor)
                .targetType(type)
                .targetId(targetId)
                .action(action)
                .reason(reason)
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

    private ModerationLogResponse mapToDto(ModerationLog log) {
        return ModerationLogResponse.builder()
                .id(log.getId())
                .action(log.getAction()) // BLOCK / UNBLOCK / AUTO_BAN
                .reason(log.getReason())
                .actorName(log.getActor() != null ? log.getActor().getDisplayName() : "System (AI)")
                .createdAt(log.getCreatedAt())
                .build();
    }

    /**
     * Hàm helper chung để điền số lượng Report và Complaint
     */
    private <T> void enrichWithCounts(
            List<T> responses,
            Function<T, Object> idExtractor, // Nhận Object (Long hoặc String)
            BiConsumer<T, Long> setReport,
            BiConsumer<T, Long> setComplaint,
            TargetType type
    ) {
        if (responses.isEmpty()) return;

        // 1. Convert tất cả ID sang List<String> để query
        List<String> ids = responses.stream()
                .map(idExtractor)
                .map(String::valueOf)
                .toList();

        // 2. Lấy Report Count
        Map<String, Long> reportCounts = new HashMap<>();
        try {
            reportRepository.countByTargetTypeAndTargetIdIn(type, ids)
                    .forEach(item -> {
                        String key = String.valueOf(item.getId());
                        reportCounts.put(key, item.getCount());
                    });
        } catch (Exception e) {
            log.error("Lỗi khi lấy Report Count: {}", e.getMessage());
        }

        // 3. Lấy Complaint Count (Xử lý an toàn nếu chưa implement)
        Map<String, Long> complaintCounts = new HashMap<>();
        if (complaintRepository != null) { // Check null safety
            try {
                complaintRepository.countByTargetTypeAndTargetIdIn(type, ids)
                        .forEach(item -> {
                            String key = String.valueOf(item.getId());
                            complaintCounts.put(key, item.getCount());
                        });
            } catch (Exception e) {
                // Log warning thôi, không làm crash app nếu Complaint lỗi
                log.warn("Lỗi khi lấy Complaint Count (Có thể chưa update Repo): {}", e.getMessage());
            }
        }

        // 4. Gán dữ liệu ngược lại vào DTO
        for (T res : responses) {
            String idStr = String.valueOf(idExtractor.apply(res));

            setReport.accept(res, reportCounts.getOrDefault(idStr, 0L));
            setComplaint.accept(res, complaintCounts.getOrDefault(idStr, 0L)); // An toàn vì map đã khởi tạo
        }
    }

    // Helper build page
    private <T> PageVO<T> buildPageVO(Page<?> page, List<T> content) {
        return PageVO.<T>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    // Helper method để map và filter
    private GroupedFlaggedMessageResponse mapToGroupedResponse(Conversation convo) {
        // Lọc trong JSON list: chỉ lấy tin nhắn đã xóa
        List<ModerationMessageResponse> flaggedMsgs = convo.getMessages().stream()
                .filter(msg -> msg.get("deletedAt") != null || Boolean.TRUE.equals(msg.get("isDeleted")))
                .map(msg -> ModerationMessageResponse.builder()
                        .id(String.valueOf(msg.get("id")))
                        .senderId(parseLongSafely(msg.get("senderId")))
                        .senderName((String) msg.get("senderName"))
                        .content((String) msg.get("content"))
                        .sentAt(String.valueOf(msg.get("createdAt")))
                        .deletedAt(parseInstantSafely(msg.get("deletedAt")))
                        .isSystemBan(Boolean.TRUE.equals(msg.get("isSystemBan")))
                        .build())
                // Sort tin nhắn vi phạm mới nhất lên đầu (hoặc cũ nhất tùy bạn)
                .sorted(Comparator.comparing(ModerationMessageResponse::getSentAt).reversed())
                .toList();

        return GroupedFlaggedMessageResponse.builder()
                .conversationId(convo.getId())
                .conversationTitle(convo.getTitle() != null ? convo.getTitle() : "Cuộc trò chuyện") // Có thể xử lý tên nếu là chat 1-1
                .isGroup(Boolean.TRUE.equals(convo.getIsGroup()))
                .lastUpdatedAt(convo.getUpdatedAt())
                .flaggedMessages(flaggedMsgs)
                .build();
    }

    private Instant parseInstantSafely(Object obj) {
        if (obj == null) return null;
        try {
            return Instant.parse(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }

    // Helper parse Long an toàn
    private Long parseLongSafely(Object obj) {
        if (obj == null) return null;
        try {
            return Long.valueOf(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Hàm helper nhỏ để xử lý chuỗi filter (giữ lại từ code cũ nếu cần)
    private String extractKeyword(String filter) {
        if (filter != null && filter.contains("content=='")) {
            try {
                return filter.split("content=='")[1].split("'")[0];
            } catch (Exception e) {
                return null;
            }
        }
        return filter; // Trả về nguyên gốc nếu client gửi string thường
    }

    private Pageable mapNativeQueryPageable(Pageable pageable, String type) {
        // 1. Nếu không sort gì cả -> Default sort
        if (pageable.getSort().isUnsorted()) {
            String defaultSortSql;
            if ("MESSAGE".equals(type)) {
                defaultSortSql = "CAST(msg ->> 'createdAt' AS TIMESTAMP)";
            } else {
                defaultSortSql = "created_at";
            }
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    JpaSort.unsafe(Sort.Direction.DESC, defaultSortSql));
        }

        // 2. Map các field sort từ Frontend -> Database
        List<Sort> sorts = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            String property = order.getProperty();
            Sort.Direction direction = order.getDirection();

            String sqlExpression = property;

            if ("createdAt".equals(property)) {
                if ("MESSAGE".equals(type)) {
                    sqlExpression = "CAST(msg ->> 'createdAt' AS TIMESTAMP)";
                } else {
                    sqlExpression = "created_at";
                }
            }
            // Xử lý field 'updatedAt'
            else if ("updatedAt".equals(property)) {
                if ("MESSAGE".equals(type)) {
                    sqlExpression = "updated_at";
                } else {
                    sqlExpression = "updated_at";
                }
            }
            // Nếu lỡ truyền 'created_at' snake_case từ FE
            else if ("created_at".equals(property)) {
                sqlExpression = "created_at";
            }

            // Add vào list sort
            sorts.add(JpaSort.unsafe(direction, sqlExpression));
        });

        Sort finalSort = Sort.unsorted();
        for (Sort s : sorts) {
            finalSort = finalSort.and(s);
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), finalSort);
    }

    private GroupedFlaggedMessageResponse mapToGroupedResponse(Conversation c, Set<String> reportedIds) {
        List<ModerationMessageResponse> flaggedMsgs = new ArrayList<>();

        if (c.getMessages() != null) {
            for (Map<String, Object> msgMap : c.getMessages()) {
                String msgId = (String) msgMap.get("id");

                // Check 1: Đã bị xóa (System ban hoặc delete)
                // Lưu ý: JSONB trả về có thể là String "true" hoặc Boolean true tùy driver
                boolean isDeleted = msgMap.get("deletedAt") != null
                        || Boolean.TRUE.equals(msgMap.get("isDeleted"))
                        || "true".equalsIgnoreCase(String.valueOf(msgMap.get("isDeleted")));

                // Check 2: Có nằm trong danh sách bị report không
                boolean isReported = reportedIds.contains(msgId);

                // LOGIC QUAN TRỌNG: Chỉ lấy nếu (Đã xóa) HOẶC (Bị report)
                if (isDeleted || isReported) {
                    flaggedMsgs.add(mapJsonToDto(msgMap, c.getId()));
                }
            }
        }

        // Enrich: Điền số lượng report/complaint cho từng tin nhắn trong list này
        if (!flaggedMsgs.isEmpty()) {
            try {
                enrichWithCounts(flaggedMsgs,
                        ModerationMessageResponse::getId,
                        ModerationMessageResponse::setReportCount,
                        ModerationMessageResponse::setComplaintCount,
                        TargetType.MESSAGE);
            } catch (Exception e) {
                log.error("Error enriching counts for grouped messages conversation {}: {}", c.getId(), e.getMessage());
            }
        }

        return GroupedFlaggedMessageResponse.builder()
                .conversationId(c.getId())
                .conversationTitle(c.getTitle()) // Có thể null nếu là chat 1-1
                .isGroup(c.getIsGroup())
                .flaggedMessages(flaggedMsgs)
                .build();
    }

    /**
     * Helper 2: Convert từ Map (JSONB) sang DTO
     */
    private ModerationMessageResponse mapJsonToDto(Map<String, Object> msgMap, Long conversationId) {
        // 1. Parse ID & Basic fields
        String id = (String) msgMap.get("id");
        Long senderId = msgMap.get("senderId") != null ? ((Number) msgMap.get("senderId")).longValue() : null;
        String senderName = (String) msgMap.get("senderName");
        String senderAvatar = (String) msgMap.get("senderAvatar");
        String content = (String) msgMap.get("content");

        // 2. Parse Date (SentAt)
        String sentAtStr = (String) msgMap.get("createdAt");
        String sentAt = null;
        if (sentAtStr != null) {
            sentAt = sentAtStr; // Giữ nguyên String ISO hoặc parse sang Instant.toString() nếu cần chuẩn hóa
        }

        // 3. Parse Date (DeletedAt)
        Object deletedAtObj = msgMap.get("deletedAt");
        Instant deletedAt = null;
        if (deletedAtObj != null) {
            try {
                deletedAt = Instant.parse(deletedAtObj.toString());
            } catch (Exception e) {
                log.warn("Cannot parse deletedAt for msg {}: {}", id, deletedAtObj);
            }
        }

        // 4. Parse Media
        // Trong Conversation Entity, messages thường là List<Map>, nên media bên trong cũng là List<Map> hoặc List<Object>
        List<Map<String, Object>> mediaList = new ArrayList<>();
        Object mediaObj = msgMap.get("media");
        if (mediaObj instanceof List) {
            mediaList = (List<Map<String, Object>>) mediaObj;
        } else if (mediaObj instanceof String && !((String) mediaObj).isEmpty()) {
            // Trường hợp hy hữu nó lưu dạng String JSON
            try {
                mediaList = objectMapper.readValue((String) mediaObj, new TypeReference<>() {});
            } catch (Exception e) {
                log.error("Error parsing media string for msg {}", id);
            }
        }

        return ModerationMessageResponse.builder()
                .id(id)
                .conversationId(conversationId)
                .senderId(senderId)
                .senderName(senderName)
                .senderAvatar(senderAvatar)
                .content(content)
                .sentAt(sentAt)
                .deletedAt(deletedAt)
                .media(mediaList)
                .reportCount(0L)
                .complaintCount(0L)
                .build();
    }
}

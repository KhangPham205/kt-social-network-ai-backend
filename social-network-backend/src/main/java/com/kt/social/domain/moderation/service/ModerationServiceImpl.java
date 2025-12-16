package com.kt.social.domain.moderation.service;

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
import com.kt.social.domain.report.mapper.ReportMapper;
import com.kt.social.domain.report.model.Report;
import com.kt.social.domain.report.repository.ComplaintRepository;
import com.kt.social.domain.report.repository.ReportRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

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
        List<Map<String, String>> mediaList = new ArrayList<>();
        Object mediaObj = messageData.get("media");

        if (mediaObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawMap) {
                    try {
                        Map<String, String> safeMap = new HashMap<>();
                        safeMap.put("url", String.valueOf(rawMap.get("url")));
                        Object typeObj = rawMap.get("type");
                        safeMap.put("type", typeObj != null ? String.valueOf(typeObj) : "file");                        mediaList.add(safeMap);
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
        try {
            // Nếu bạn lưu targetId trong bảng Report là String -> Dùng thẳng messageId
            // Nếu lưu là Long -> Cần hash hoặc logic khác. Ở đây giả định bạn xử lý được việc map ID.
            // reportCount = reportRepository.countByTargetTypeAndTargetId(TargetType.MESSAGE, messageId);
        } catch (Exception e) {
            // Log error
        }

        // 7. Map sang DTO
        return ModerationMessageResponse.builder()
                .id(messageId)
                .conversationId(conversation.getId())
                .senderId(senderId)
                .senderName(senderName)
                .senderAvatar(senderAvatar)
                .content((String) messageData.get("content"))
                .sentAt(sentAtStr)
                .media(mediaList) // 🔥 Map đúng field List<Map>
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
        // Query bài viết vi phạm
        Specification<Post> spec = (root, query, cb) -> cb.or(
                cb.isNotNull(root.get("deletedAt"))
//                cb.isTrue(root.get("isSystemBan"))
        );
        Page<Post> page = postRepository.findAll(spec, pageable);

        // Convert sang DTO
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

        // 🔥 FIX: Không dùng findAll(spec) nữa
        Page<Comment> page;

        if (filter != null && !filter.isBlank()) {
            page = commentRepository.findDeletedCommentsWithFilter(filter, pageable);
        } else {
            page = commentRepository.findDeletedComments(pageable);
        }

        List<CommentResponse> content = page.getContent().stream()
                .map(commentMapper::toDto)
                .toList();

        // Gọi hàm bổ sung count (giữ nguyên logic của bạn)
        enrichWithCounts(content, CommentResponse::getId, CommentResponse::setReportCount, CommentResponse::setComplaintCount, TargetType.COMMENT);

        return buildPageVO(page, content);
    }

    // --- 3. MESSAGE ---
    @Override
    @Transactional(readOnly = true)
    public PageVO<ModerationMessageResponse> getFlaggedMessages(String filter, Pageable pageable) {
        // Giả sử repository trả về DTO luôn (như đã bàn ở câu trước)
        Page<FlaggedMessageProjection> page = conversationRepository.findDeletedMessages(pageable);

        List<ModerationMessageResponse> content = page.getContent().stream()
                .map(p -> {
                    // --- XỬ LÝ MAP MEDIA ---
                    List<Map<String, String>> mediaList = new ArrayList<>();
                    Object mediaObj = p.getMedia();

                    // Tùy thuộc vào Hibernate Dialect, mediaObj có thể là String (JSON) hoặc List
                    // Case 1: Nếu Hibernate tự map ra List (tốt nhất)
                    if (mediaObj instanceof List) {
                        mediaList = (List<Map<String, String>>) mediaObj;
                    }
                    // Case 2: Nếu Hibernate trả về String JSON -> Cần parse thủ công (Ví dụ dùng Jackson)
                    // Ở đây để đơn giản, ta giả định driver/dialect hỗ trợ map thẳng hoặc ta bỏ qua bước parse phức tạp
                    // Nếu bạn gặp lỗi trả về String, bạn cần dùng ObjectMapper để readValue.

                    return ModerationMessageResponse.builder()
                            .id(p.getId())
                            .conversationId(p.getConversationId())
                            .senderId(p.getSenderId())
                            .senderName(p.getSenderName())
                            .senderAvatar(p.getSenderAvatar())
                            .content(p.getContent())
                            .sentAt(p.getSentAt())
                            .deletedAt(p.getDeletedAt())
                            .media(mediaList)
                            .build();
                })
                .collect(Collectors.toList());

        // GỌI HÀM BỔ SUNG COUNT
        // Lưu ý: Message ID thường là String (UUID). Nếu Report lưu targetId là Long thì sẽ lỗi ở đây.
        // Giả sử bạn đã parse Message ID sang Long hoặc Report hỗ trợ String.
        // Nếu Message ID là String UUID: Bạn cần sửa hàm countByTargetTypeAndTargetIdIn nhận List<String>
        try {
            enrichWithCounts(content,
                    msg -> Long.valueOf(msg.getId()), // Parse ID tin nhắn sang Long
                    ModerationMessageResponse::setReportCount,
                    ModerationMessageResponse::setComplaintCount,
                    TargetType.MESSAGE);
        } catch (NumberFormatException e) {
            // Log warning: Message ID không phải số, không thể fetch report theo ID số
        }

        return buildPageVO(page, content);
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
    public void blockContent(Object id, TargetType targetType) { // Đổi Long id -> Object id hoặc String id
        User admin = userService.getCurrentUser();
        String idStr = String.valueOf(id); // Chuyển về String để xử lý chung

        if (targetType == TargetType.POST) {
            Long postId = Long.valueOf(idStr); // Parse lại Long cho Post
            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

            post.setDeletedAt(Instant.now());
            post.setSystemBan(true);
            postRepository.save(post);

        } else if (targetType == TargetType.COMMENT) {
            Long commentId = Long.valueOf(idStr); // Parse lại Long cho Comment
            Comment comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

            comment.setDeletedAt(Instant.now());
            commentRepository.save(comment);

        } else if (targetType == TargetType.MESSAGE) {
            // 🔥 LOGIC MỚI CHO MESSAGE
            // Gọi sang MessageService để xử lý logic JSON
            messageService.softDeleteMessage(idStr);
        }

        // Ghi Log
        // Lưu ý: targetId trong log của bạn đang là Long, có thể cần sửa entity ModerationLog
        // để targetId là String nếu muốn lưu UUID message.
        saveLog(admin, targetType, idStr, "BLOCK", "Admin blocked content");
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

//    @Override
//    @Transactional
//    public void unblock(Long id, TargetType targetType) {
//        User admin = userService.getCurrentUser();
//
//        // 1. Khôi phục nội dung (Post/Comment)
//        if (targetType == TargetType.POST) {
//            // Lưu ý: Cần dùng hàm find riêng để tìm được cả bài đã bị soft-delete
//            Post post = postRepository.findByIdIncludingDeleted(id)
//                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
//
//            post.setDeletedAt(null);
//            post.setSystemBan(false);
//            post.setViolationDetails(null); // Xóa lý do vi phạm cũ (tuỳ chọn)
//            postRepository.save(post);
//
//        } else if (targetType == TargetType.COMMENT) {
//            Comment comment = commentRepository.findByIdIncludingDeleted(id)
//                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
//
//            comment.setDeletedAt(null);
//            // comment.setSystemBan(false); // Nếu comment có field này
//            commentRepository.save(comment);
//        }
//
//        // Tìm tất cả các report ĐÃ DUYỆT (APPROVED) liên quan đến nội dung này
//        List<Report> relatedReports = reportRepository.findAllByTargetTypeAndTargetIdAndStatus(
//                targetType, id, ReportStatus.APPROVED
//        );
//
//        if (!relatedReports.isEmpty()) {
//            for (Report report : relatedReports) {
//                // Ghi lại lịch sử thay đổi của Report
//                report.getHistory().add(Report.ReportHistory.builder()
//                        .actorId(admin.getId())
//                        .actorName(admin.getDisplayName())
//                        .oldStatus(ReportStatus.APPROVED)
//                        .newStatus(ReportStatus.REJECTED)
//                        .note("System: Tự động từ chối do Admin đã khôi phục nội dung gốc.")
//                        .timestamp(Instant.now())
//                        .build());
//
//                // Đổi trạng thái thành REJECTED (Coi như báo cáo sai/không còn hiệu lực)
//                report.setStatus(ReportStatus.REJECTED);
//            }
//            reportRepository.saveAll(relatedReports);
//        }
//
//        // 3. Ghi Log Moderation (Admin Action)
//        moderationLogRepository.save(ModerationLog.builder()
//                .targetType(targetType)
//                .targetId(id)
//                .action("ADMIN_RESTORE")
//                .actor(admin)
//                .reason("Admin restored content manually")
//                .createdAt(Instant.now())
//                .build());
//    }

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

    private <T> void enrichWithCounts(List<T> responses, Function<T, Long> idExtractor, BiConsumer<T, Long> setReport, BiConsumer<T, Long> setComplaint, TargetType type) {
        if (responses.isEmpty()) return;

        // 1. Lấy danh sách ID
        List<Long> ids = responses.stream().map(idExtractor).toList();

        // 2. Query Database (Chỉ tốn 2 query cho cả trang dữ liệu)
        Map<Long, Long> reportCounts = reportRepository.countByTargetTypeAndTargetIdIn(type, ids)
                .stream().collect(Collectors.toMap(IdCount::getId, IdCount::getCount));

        Map<Long, Long> complaintCounts = complaintRepository.countByTargetTypeAndTargetIdIn(type, ids)
                .stream().collect(Collectors.toMap(IdCount::getId, IdCount::getCount));

        // 3. Gán dữ liệu vào DTO
        for (T res : responses) {
            Long id = idExtractor.apply(res);
            setReport.accept(res, reportCounts.getOrDefault(id, 0L));
            setComplaint.accept(res, complaintCounts.getOrDefault(id, 0L));
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

    // Helper parse Long an toàn
    private Long parseLongSafely(Object obj) {
        if (obj == null) return null;
        try {
            return Long.valueOf(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.kt.social.domain.message.service.impl;

import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.domain.message.dto.*;
import com.kt.social.domain.message.enums.ConversationRole;
import com.kt.social.domain.message.enums.MessageType;
import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.model.ConversationMember;
import com.kt.social.domain.message.model.ConversationMemberId;
import com.kt.social.domain.message.repository.ConversationMemberRepository;
import com.kt.social.domain.message.repository.ConversationRepository;
import com.kt.social.domain.message.service.ConversationService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    // Constants cho JSON Keys
    private static final String KEY_ID = "id";
    private static final String KEY_SENDER_ID = "senderId";
    private static final String KEY_SENDER_NAME = "senderName";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TYPE = "type";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_READ_BY = "readBy";

    @Override
    @Transactional
    public ConversationResponse createConversation(ConversationCreateRequest req) {
        User creator = userService.getCurrentUser();

        // 1. CHAT 1-1: KIỂM TRA TRÙNG LẶP
        if (Boolean.FALSE.equals(req.getIsGroup())) {
            if (req.getMemberIds() == null || req.getMemberIds().isEmpty()) {
                throw new BadRequestException("Private conversation must have a recipient.");
            }

            Long recipientId = req.getMemberIds().get(0);

            // Chặn chat với chính mình (hoặc cho phép tùy nghiệp vụ, ở đây tạm chặn để tránh lỗi logic)
            if (creator.getId().equals(recipientId)) {
                throw new BadRequestException("Cannot create conversation with yourself.");
            }

            // Gọi Repo kiểm tra xem đã có conversation giữa 2 người này chưa
            Optional<Conversation> existing = conversationRepository
                    .findExistingPrivateConversation(creator.getId(), recipientId);

            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        // 2. TẠO MỚI (GROUP HOẶC 1-1 CHƯA TỒN TẠI)
        String mediaUrl = null;
        if (req.getMedia() != null && !req.getMedia().isEmpty()) {
            mediaUrl = storageService.saveFile(req.getMedia(), "conversations/" + UUID.randomUUID());
        }

        Conversation convo = Conversation.builder()
                .isGroup(Boolean.TRUE.equals(req.getIsGroup()))
                .title(req.getTitle()) // Với 1-1 title thường null
                .mediaUrl(mediaUrl)
                .messages(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Conversation saved = conversationRepository.save(convo);

        // Tạo danh sách thành viên cần lưu
        List<ConversationMember> membersToSave = new ArrayList<>();

        // Add Owner
        membersToSave.add(ConversationMember.builder()
                .id(new ConversationMemberId(saved.getId(), creator.getId()))
                .conversation(saved)
                .user(creator)
                .joinedAt(Instant.now())
                .role(ConversationRole.OWNER)
                .build());

        // Add Members
        if (req.getMemberIds() != null) {
            Set<Long> uniqueIds = new HashSet<>(req.getMemberIds());
            uniqueIds.remove(creator.getId()); // Tránh add trùng creator

            if (!uniqueIds.isEmpty()) {
                List<User> users = userRepository.findAllById(uniqueIds);
                if (users.size() != uniqueIds.size()) {
                    throw new ResourceNotFoundException("Some users not found");
                }

                for (User member : users) {
                    membersToSave.add(ConversationMember.builder()
                            .id(new ConversationMemberId(saved.getId(), member.getId()))
                            .conversation(saved)
                            .user(member)
                            .joinedAt(Instant.now())
                            .role(ConversationRole.MEMBER)
                            .build());
                }
            }
        }
        memberRepository.saveAll(membersToSave);

        // Gửi tin nhắn hệ thống nếu là Group
        if (Boolean.TRUE.equals(saved.getIsGroup())) {
            saveAndSendSystemMessage(saved, creator, creator.getDisplayName() + " created the group conversation.");
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse updateConversation(Long currentUserId, UpdateConversationRequest request) {
        ConversationMember member = checkGroupAndGetMember(request.getConversationId(), currentUserId);
        Conversation conversation = member.getConversation();

        if (member.getRole() == ConversationRole.MEMBER) {
            throw new AccessDeniedException("Only OWNER or ADMIN can update group information.");
        }

        boolean isUpdated = false;
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            conversation.setTitle(request.getTitle());
            isUpdated = true;
        }

        if (request.getMediaFile() != null && !request.getMediaFile().isEmpty()) {
            if (conversation.getMediaUrl() != null) storageService.deleteFile(conversation.getMediaUrl());
            String newMediaUrl = storageService.saveFile(request.getMediaFile(), "conversations/" + conversation.getId());
            conversation.setMediaUrl(newMediaUrl);
            isUpdated = true;
        }

        if (isUpdated) {
            conversation.setUpdatedAt(Instant.now());
            conversationRepository.save(conversation);

            // Gửi tin nhắn hệ thống báo cập nhật
            saveAndSendSystemMessage(conversation, member.getUser(),
                    member.getUser().getDisplayName() + " updated group info.");
        }

        return toConversationSummaryDto(conversation, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse addMembersToGroup(Long currentUserId, AddMembersRequest request) {
        ConversationMember currentUserMember = checkGroupAndGetMember(request.getConversationId(), currentUserId);
        Conversation conversation = currentUserMember.getConversation();

        if (currentUserMember.getRole() == ConversationRole.MEMBER) {
            throw new AccessDeniedException("Only OWNER or ADMIN can add new members");
        }

        // Lấy list ID hiện tại để lọc trùng
        Set<Long> existingMemberIds = memberRepository.findByConversationId(conversation.getId())
                .stream().map(m -> m.getUser().getId()).collect(Collectors.toSet());

        List<Long> newMemberIds = request.getUserIds().stream()
                .filter(id -> !existingMemberIds.contains(id))
                .distinct()
                .toList();

        if (newMemberIds.isEmpty()) return toConversationSummaryDto(conversation, currentUserId);

        List<User> newUsers = userRepository.findAllById(newMemberIds);
        List<ConversationMember> newMembers = newUsers.stream()
                .map(user -> ConversationMember.builder()
                        .id(new ConversationMemberId(conversation.getId(), user.getId()))
                        .conversation(conversation)
                        .user(user)
                        .role(ConversationRole.MEMBER)
                        .joinedAt(Instant.now())
                        .build())
                .toList();
        memberRepository.saveAll(newMembers);

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        String addedNames = newUsers.stream().map(User::getDisplayName).collect(Collectors.joining(", "));
        saveAndSendSystemMessage(conversation, currentUserMember.getUser(),
                currentUserMember.getUser().getDisplayName() + " added " + addedNames + ".");

        return toConversationSummaryDto(conversation, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse removeMemberFromGroup(Long currentUserId, Long conversationId, Long userIdToRemove) {
        ConversationMember currentUserMember = checkGroupAndGetMember(conversationId, currentUserId);

        // Optimize: Tìm trực tiếp member cần xóa
        ConversationMember targetMember = memberRepository.findByConversationIdAndUserId(conversationId, userIdToRemove)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in group."));

        if (currentUserId.equals(userIdToRemove)) {
            throw new BadRequestException("Use leaveConversation to remove yourself.");
        }

        validateRemovePermission(currentUserMember.getRole(), targetMember.getRole());

        String removedUserName = targetMember.getUser().getDisplayName();
        memberRepository.delete(targetMember);

        Conversation conversation = currentUserMember.getConversation();
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        saveAndSendSystemMessage(conversation, currentUserMember.getUser(),
                currentUserMember.getUser().getDisplayName() + " removed " + removedUserName + ".");

        return toConversationSummaryDto(conversation, currentUserId);
    }

    @Override
    @Transactional
    public void leaveConversation(Long currentUserId, Long conversationId) {
        ConversationMember member = checkGroupAndGetMember(conversationId, currentUserId);
        Conversation conversation = member.getConversation();

        if (member.getRole() == ConversationRole.OWNER) {
            long memberCount = memberRepository.countByConversationId(conversationId);
            if (memberCount > 1) {
                throw new BadRequestException("Owner must transfer ownership before leaving.");
            } else {
                // Nhóm chỉ còn 1 người -> Xóa luôn nhóm
                conversationRepository.delete(conversation);
                // Notify socket xóa nhóm
                Map<String, Object> payload = Map.of(
                        "type", "EVENT_CONVERSATION_DELETED",
                        "conversationId", conversationId
                );
                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, payload);
                return;
            }
        }

        memberRepository.delete(member);

        // Nếu là group thì báo tin nhắn hệ thống
        if (Boolean.TRUE.equals(conversation.getIsGroup())) {
            conversation.setUpdatedAt(Instant.now());
            conversationRepository.save(conversation);
            saveAndSendSystemMessage(conversation, member.getUser(), member.getUser().getDisplayName() + " left.");
        }
    }

    @Override
    @Transactional
    public ConversationSummaryResponse updateMemberRole(Long currentUserId, UpdateMemberRoleRequest request) {
        ConversationMember currentUserMember = checkGroupAndGetMember(request.getConversationId(), currentUserId);

        if (currentUserMember.getRole() == ConversationRole.MEMBER) {
            throw new AccessDeniedException("Permission denied.");
        }

        ConversationMember targetMember = memberRepository.findByConversationIdAndUserId(request.getConversationId(), request.getUserIdToChange())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found."));

        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BadRequestException("Cannot change OWNER role.");
        }

        // Validate request role
        if (request.getNewRole() == ConversationRole.OWNER) {
            throw new BadRequestException("Cannot assign OWNER via this API.");
        }

        targetMember.setRole(request.getNewRole());
        memberRepository.save(targetMember);

        Conversation conversation = currentUserMember.getConversation();
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        String roleName = request.getNewRole().name();
        saveAndSendSystemMessage(conversation, currentUserMember.getUser(),
                targetMember.getUser().getDisplayName() + " is now " + roleName + ".");

        return toConversationSummaryDto(conversation, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> getUserConversations(Long userId) {
        List<ConversationMember> members = memberRepository.findConversationsByUserId(userId);

        return members.stream()
                .map(ConversationMember::getConversation)
                .distinct()
                .sorted(Comparator.comparing(
                        (Conversation c) -> c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .map(c -> toConversationSummaryDto(c, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationSummaryResponse getConversationById(Long currentUserId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isMember = memberRepository.existsByConversationIdAndUserId(conversationId, currentUserId);
        if (!isMember) {
            throw new AccessDeniedException("Not a member.");
        }

        return toConversationSummaryDto(conversation, currentUserId);
    }

    @Override
    @Transactional
    public void findOrCreateDirectConversation(Long userAId, Long userBId) {

        if (userAId.equals(userBId)) {
            return;
        }

        Optional<Conversation> existing = conversationRepository.findExistingPrivateConversation(userAId, userBId);

        if (existing.isPresent()) {
            return;
        }

        User userA = userRepository.findById(userAId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userAId));
        User userB = userRepository.findById(userBId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userBId));

        Conversation conversation = Conversation.builder()
                .isGroup(false)
                .title(null)
                .mediaUrl(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .messages(new ArrayList<>())
                .build();

        Conversation savedConversation = conversationRepository.save(conversation);

        ConversationMember memberA = ConversationMember.builder()
                .id(new ConversationMemberId(savedConversation.getId(), userA.getId()))
                .conversation(savedConversation)
                .user(userA)
                .role(ConversationRole.MEMBER)
                .joinedAt(Instant.now())
                .build();

        ConversationMember memberB = ConversationMember.builder()
                .id(new ConversationMemberId(savedConversation.getId(), userB.getId()))
                .conversation(savedConversation)
                .user(userB)
                .role(ConversationRole.MEMBER)
                .joinedAt(Instant.now())
                .build();

        memberRepository.saveAll(List.of(memberA, memberB));

        // (Tùy chọn) Có thể gửi socket event "NEW_CONVERSATION" tới 2 user này
        // để danh sách chat của họ tự động cập nhật mà không cần F5.
    }

    @Override
    @Transactional
    public void markMessageAsRead(Long userId, MarkReadRequest request) {
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!memberRepository.existsByConversationIdAndUserId(conversation.getId(), userId)) {
            throw new AccessDeniedException("Not a member.");
        }

        List<Map<String, Object>> messages = conversation.getMessages();
        if (messages == null || messages.isEmpty()) return;

        boolean isUpdated = false;

        // 🔥 OPTIMIZATION: Duyệt ngược từ cuối lên (tin mới nhất)
        // Vì người dùng thường đọc tin mới nhất.
        // Giới hạn tìm kiếm trong 50 tin gần nhất để tránh treo nếu list quá dài
        int limit = 50;
        int count = 0;

        for (int i = messages.size() - 1; i >= 0 && count < limit; i--) {
            Map<String, Object> msg = messages.get(i);
            String msgId = (String) msg.get(KEY_ID);

            if (msgId != null && msgId.equals(request.getMessageId())) {
                List<String> readBy = (List<String>) msg.get(KEY_READ_BY); // Dùng String ID cho an toàn với JSON
                if (readBy == null) {
                    readBy = new ArrayList<>();
                    msg.put(KEY_READ_BY, readBy);
                }

                String userIdStr = String.valueOf(userId);
                if (!readBy.contains(userIdStr)) {
                    readBy.add(userIdStr);
                    isUpdated = true;

                    // Gửi Socket Event
                    Map<String, Object> payload = new HashMap<>();
                    payload.put(KEY_TYPE, "EVENT_READ");
                    payload.put("conversationId", conversation.getId());
                    payload.put("messageId", msgId);
                    payload.put("readerId", userId);
                    payload.put("timestamp", Instant.now().toString());
                    messagingTemplate.convertAndSend("/queue/conversation/" + conversation.getId(), payload);
                }
                break; // Tìm thấy thì dừng ngay
            }
            count++;
        }

        if (isUpdated) {
            conversationRepository.save(conversation);
        }
    }

    // ------------------------- HELPER METHODS -------------------------
    private ConversationSummaryResponse toConversationSummaryDto(Conversation c, Long viewerId) {
        // 1. XỬ LÝ LAST MESSAGE
        Map<String, Object> processedLastMessage = null;

        if (c.getMessages() != null && !c.getMessages().isEmpty()) {
            Map<String, Object> rawMsg = c.getMessages().get(c.getMessages().size() - 1);

            processedLastMessage = new HashMap<>(rawMsg);

            boolean isDeleted = rawMsg.get("deletedAt") != null
                    || Boolean.TRUE.equals(rawMsg.get("isDeleted"))
                    || Boolean.TRUE.equals(rawMsg.get("isSystemBan"));

            if (isDeleted) {
                processedLastMessage.put("content", "Tin nhắn đã bị gỡ bỏ");

                processedLastMessage.put("media", null);
                processedLastMessage.put("type", "REVOKED"); // Đánh dấu loại tin để FE render màu xám/nghiêng
            }
        }

        // 2. Xử lý Participants
        List<ParticipantDto> participants = c.getMembers().stream()
                .map(m -> ParticipantDto.builder()
                        .id(m.getUser().getId())
                        .displayName(m.getUser().getDisplayName())
                        .avatarUrl(m.getUser().getAvatarUrl())
                        .role(m.getRole().name())
                        .build())
                .toList();

        // 3. Xử lý Title và Avatar hội thoại
        String finalTitle = c.getTitle();
        String finalMediaUrl = c.getMediaUrl();

        // Logic hiển thị tên/ảnh cho chat 1-1
        if (Boolean.FALSE.equals(c.getIsGroup())) {
            ParticipantDto otherUser = participants.stream()
                    .filter(p -> !p.getId().equals(viewerId))
                    .findFirst()
                    .orElse(null);

            if (otherUser != null) {
                finalTitle = otherUser.getDisplayName();
                finalMediaUrl = otherUser.getAvatarUrl();
            }
        }

        // 4. Build Response
        return ConversationSummaryResponse.builder()
                .id(c.getId())
                .title(finalTitle)
                .mediaUrl(finalMediaUrl)
                .isGroup(Boolean.TRUE.equals(c.getIsGroup()))
                .lastMessage(processedLastMessage) // Truyền map đã xử lý vào
                .participants(participants)
                .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt())
                .build();
    }

    private ConversationMember checkGroupAndGetMember(Long conversationId, Long userId) {
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of conversation " + conversationId));

        // Nếu repo không fetch conversation, cần load
        if (member.getConversation() == null) {
            member.setConversation(conversationRepository.findById(conversationId).orElseThrow());
        }

        // Chỉ group mới thực hiện được các hành động này
        if (Boolean.FALSE.equals(member.getConversation().getIsGroup())) {
            throw new BadRequestException("Action only valid for groups.");
        }
        return member;
    }

    private void validateRemovePermission(ConversationRole actor, ConversationRole target) {
        if (actor == ConversationRole.MEMBER) throw new AccessDeniedException("Permission denied.");
        if (target == ConversationRole.OWNER) throw new AccessDeniedException("Cannot remove Owner.");
        if (actor == ConversationRole.ADMIN && target == ConversationRole.ADMIN) throw new AccessDeniedException("Admin cannot remove another Admin.");
    }

    private void saveAndSendSystemMessage(Conversation conversation, User sender, String content) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put(KEY_ID, UUID.randomUUID().toString());
        messageMap.put(KEY_SENDER_ID, sender.getId());
        messageMap.put(KEY_SENDER_NAME, sender.getDisplayName());
        messageMap.put(KEY_CONTENT, content);
        messageMap.put(KEY_TYPE, MessageType.SYSTEM.name());
        messageMap.put(KEY_CREATED_AT, Instant.now().toString());
        messageMap.put(KEY_READ_BY, new ArrayList<String>()); // Init empty read list

        if (conversation.getMessages() == null) {
            conversation.setMessages(new ArrayList<>());
        }
        conversation.getMessages().add(messageMap); // Add vào cuối danh sách

        conversationRepository.save(conversation);

        // Send Socket
        Map<String, Object> socketPayload = new HashMap<>(messageMap);
        socketPayload.put("conversationId", conversation.getId());
        socketPayload.put("avatarUrl", sender.getAvatarUrl());
        messagingTemplate.convertAndSend("/queue/conversation/" + conversation.getId(), socketPayload);
    }

    private ConversationResponse mapToResponse(Conversation convo) {
        // Optimize: Thay vì query DB, nếu convo.getMembers() có sẵn thì dùng luôn
        // Nhưng an toàn nhất là query list ID
        List<Long> memberIds = memberRepository.findByConversationId(convo.getId())
                .stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toList());

        return ConversationResponse.builder()
                .id(convo.getId())
                .isGroup(convo.getIsGroup())
                .title(convo.getTitle())
                .mediaUrl(convo.getMediaUrl())
                .createdAt(convo.getCreatedAt())
                .memberIds(memberIds)
                .build();
    }
}
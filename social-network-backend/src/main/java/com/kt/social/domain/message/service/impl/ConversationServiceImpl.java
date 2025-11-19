package com.kt.social.domain.message.service.impl;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final StorageService storageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public ConversationResponse createConversation(ConversationCreateRequest req) {
        User creator = userService.getCurrentUser();

        String mediaUrl = null;
        if (req.getMedia() != null && !req.getMedia().isEmpty()) {
            mediaUrl = storageService.saveFile(req.getMedia(), "conversations/" + UUID.randomUUID());
        }

        Conversation convo = Conversation.builder()
                .isGroup(Boolean.TRUE.equals(req.getIsGroup()))
                .title(req.getTitle())
                .mediaUrl(mediaUrl)
                .messages(new ArrayList<>())
                .createdAt(Instant.now())
                .build();

        Conversation saved = conversationRepository.save(convo);

        // Người tạo là OWNER
        ConversationMember owner = ConversationMember.builder()
                .id(new ConversationMemberId(saved.getId(), creator.getId()))
                .conversation(saved)
                .user(creator)
                .joinedAt(Instant.now())
                .role(ConversationRole.OWNER)
                .build();
        memberRepository.save(owner);

        // Các thành viên khác là MEMBER
        if (req.getMemberIds() != null) {
            for (Long userId : req.getMemberIds()) {
                if (!userId.equals(creator.getId())) {
                    User member = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

                    ConversationMember cm = ConversationMember.builder()
                            .id(new ConversationMemberId(saved.getId(), member.getId()))
                            .conversation(saved)
                            .user(member)
                            .joinedAt(Instant.now())
                            .role(ConversationRole.MEMBER)
                            .build();
                    memberRepository.save(cm);
                }
            }
        }

        if (saved.getIsGroup()) {
            saveAndSendSystemMessage(saved, creator, creator.getDisplayName() + " created the group conversation.");
        }

        List<Long> memberIds = memberRepository.findByConversation(saved)
                .stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toList());

        return ConversationResponse.builder()
                .id(saved.getId())
                .isGroup(saved.getIsGroup())
                .title(saved.getTitle())
                .mediaUrl(saved.getMediaUrl())
                .createdAt(saved.getCreatedAt())
                .memberIds(memberIds)
                .build();
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

            saveAndSendSystemMessage(conversation, member.getUser(),
                    member.getUser().getDisplayName() + " updated the group information.");
        }

        return toConversationSummaryDto(conversation, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse addMembersToGroup(Long currentUserId, AddMembersRequest request) {
        ConversationMember currentUserMember = checkGroupAndGetMember(request.getConversationId(), currentUserId);
        Conversation conversation = currentUserMember.getConversation();

        if (!conversation.getIsGroup()) {
            throw new BadRequestException("You cannot add a member to 1-1 conversation");
        }

        if (currentUserMember.getRole() == ConversationRole.MEMBER) {
            throw new AccessDeniedException("Only OWNER or ADMIN can add a new member");
        }

        Set<Long> existingMemberIds = conversation.getMembers().stream()
                .map(cm -> cm.getUser().getId())
                .collect(Collectors.toSet());

        List<Long> newMemberIds = request.getUserIds().stream()
                .filter(id -> !existingMemberIds.contains(id))
                .distinct()
                .toList();

        if (newMemberIds.isEmpty()) return toConversationSummaryDto(conversation, currentUserId);

        List<User> newUsers = userRepository.findByIdIn(newMemberIds);
        List<ConversationMember> newMembers = newUsers.stream()
                .map(user -> ConversationMember.builder()
                        .id(new ConversationMemberId(request.getConversationId(), user.getId()))
                        .conversation(conversation)
                        .user(user)
                        .role(ConversationRole.MEMBER)
                        .joinedAt(Instant.now())
                        .build())
                .toList();
        conversationMemberRepository.saveAll(newMembers);

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        String addedNames = newUsers.stream().map(User::getDisplayName).collect(Collectors.joining(", "));
        saveAndSendSystemMessage(conversation, currentUserMember.getUser(),
                currentUserMember.getUser().getDisplayName() + " added " + addedNames + " into group.");

        Conversation updatedConvo = conversationRepository.findById(request.getConversationId()).get();
        return toConversationSummaryDto(updatedConvo, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse removeMemberFromGroup(Long currentUserId, Long conversationId, Long userIdToRemove) {
        ConversationMember currentUserMember = checkGroupAndGetMember(conversationId, currentUserId);
        ConversationMember targetMember = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userIdToRemove)
                .orElseThrow(() -> new ResourceNotFoundException("This member does not exist in the group."));

        if (currentUserId.equals(userIdToRemove)) {
            throw new BadRequestException("You cannot remove yourself. Use leaveConversation instead.");
        }

        ConversationRole currentUserRole = currentUserMember.getRole();
        ConversationRole targetUserRole = targetMember.getRole();

        if (currentUserRole == ConversationRole.MEMBER) throw new AccessDeniedException("Only OWNER or ADMIN can remove members.");
        if (targetUserRole == ConversationRole.OWNER) throw new AccessDeniedException("Cannot remove the OWNER from the group.");
        if (currentUserRole == ConversationRole.ADMIN && targetUserRole == ConversationRole.ADMIN)
            throw new AccessDeniedException("ADMIN cannot remove other ADMINs.");

        String removedUserName = targetMember.getUser().getDisplayName();
        conversationMemberRepository.delete(targetMember);

        Conversation conversation = currentUserMember.getConversation();
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        saveAndSendSystemMessage(conversation, currentUserMember.getUser(),
                currentUserMember.getUser().getDisplayName() + " deleted " + removedUserName + " from the group.");

        Conversation updatedConvo = conversationRepository.findById(conversationId).get();
        return toConversationSummaryDto(updatedConvo, currentUserId);
    }

    @Override
    @Transactional
    public void leaveConversation(Long currentUserId, Long conversationId) {
        ConversationMember member = checkGroupAndGetMember(conversationId, currentUserId);
        Conversation conversation = member.getConversation();
        String leaverName = member.getUser().getDisplayName();

        // Logic Owner
        if (member.getRole() == ConversationRole.OWNER) {
            // 🔥 SỬ DỤNG HÀM countByConversationId MỚI
            long memberCount = conversationMemberRepository.countByConversationId(conversationId);

            if (memberCount > 1) {
                throw new BadRequestException("You are the OWNER. Please transfer ownership or delete the group.");
            } else {
                conversationRepository.delete(conversation);
                // Nhóm giải tán -> Gửi sự kiện socket nhưng không lưu DB (vì DB đã xóa)
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "EVENT_CONVERSATION_DELETED");
                payload.put("conversationId", conversationId);
                messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, payload);
                return;
            }
        }

        conversationMemberRepository.delete(member);

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        saveAndSendSystemMessage(conversation, member.getUser(), leaverName + " left the conversation.");
    }

    @Override
    @Transactional
    public ConversationSummaryResponse updateMemberRole(Long currentUserId, UpdateMemberRoleRequest request) {
        // Lấy member thực hiện (và kiểm tra nhóm)
        ConversationMember currentUserMember = checkGroupAndGetMember(request.getConversationId(), currentUserId);

        // Lấy member bị thay đổi
        ConversationMember targetMember = conversationMemberRepository.findByConversationIdAndUserId(request.getConversationId(), request.getUserIdToChange())
                .orElseThrow(() -> new ResourceNotFoundException("This member does not exist in the group."));

        // Validate vai trò mới (Không được phép gán OWNER)
        if (request.getNewRole() == null || request.getNewRole() == ConversationRole.OWNER) {
            throw new BadRequestException("Invalid role specified. Can only assign ADMIN or MEMBER roles.");
        }

        // Kiểm tra quyền: Chỉ OWNER/ADMIN mới được làm
        if (currentUserMember.getRole() == ConversationRole.MEMBER) {
            throw new AccessDeniedException("Only OWNER or ADMIN can change member roles.");
        }

        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BadRequestException("Cannot change role of the OWNER.");
        }

        targetMember.setRole(request.getNewRole());
        conversationMemberRepository.save(targetMember);

        Conversation conversation = currentUserMember.getConversation();
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        Conversation updatedConvo = conversationRepository.findById(request.getConversationId()).get();

        String roleName = request.getNewRole() == ConversationRole.ADMIN ? "ADMIN" : "MEMBER";
        saveAndSendSystemMessage(currentUserMember.getConversation(), currentUserMember.getUser(),
                targetMember.getUser().getDisplayName() + " was authorized to be " + roleName + ".");

        return toConversationSummaryDto(updatedConvo, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> getUserConversations(Long userId) {

        List<ConversationMember> joined = conversationMemberRepository.findConversationsByUserId(userId);

        Set<Long> processedConversationIds = new HashSet<>();

        return joined.stream()
                .map(ConversationMember::getConversation)
                .filter(c -> c != null && processedConversationIds.add(c.getId()))
                .map(c -> toConversationSummaryDto(c, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationSummaryResponse getConversationById(Long currentUserId, Long conversationId) {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(currentUserId));

        if (!isMember) {
            throw new AccessDeniedException("You are not a member of this conversation.");
        }

        return toConversationSummaryDto(conversation, currentUserId);
    }

    @Override
    @Transactional
    public void findOrCreateDirectConversation(Long userAId, Long userBId) {
        List<Long> ids = Stream.of(userAId, userBId).sorted().toList();

        Optional<Conversation> existing = conversationRepository.findDirectConversationBetween(ids);
        if (existing.isPresent()) {
            Conversation c = existing.get();

            List<Long> memberIds = memberRepository.findByConversation(c)
                    .stream().map(cm -> cm.getUser().getId()).toList();

            ConversationResponse.builder()
                    .id(c.getId())
                    .isGroup(c.getIsGroup())
                    .title(c.getTitle())
                    .mediaUrl(c.getMediaUrl())
                    .createdAt(c.getCreatedAt())
                    .memberIds(memberIds)
                    .build();
            return;
        }

        User a = userRepository.findById(userAId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userAId));
        User b = userRepository.findById(userBId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userBId));

        Conversation convo = Conversation.builder()
                .isGroup(false)
                .title(null)
                .mediaUrl(null)
                .createdAt(Instant.now())
                .messages(new ArrayList<>()) // khởi tạo rỗng
                .build();

        Conversation saved = conversationRepository.save(convo);

        ConversationMember cmA = ConversationMember.builder()
                .id(new ConversationMemberId(saved.getId(), a.getId()))
                .conversation(saved)
                .user(a)
                .joinedAt(Instant.now())
                .role(ConversationRole.MEMBER)
                .build();

        ConversationMember cmB = ConversationMember.builder()
                .id(new ConversationMemberId(saved.getId(), b.getId()))
                .conversation(saved)
                .user(b)
                .joinedAt(Instant.now())
                .role(ConversationRole.MEMBER)
                .build();

        memberRepository.save(cmA);
        memberRepository.save(cmB);
    }

    @Override
    @Transactional
    public void markMessageAsRead(Long userId, MarkReadRequest request) {
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId));
        if (!isMember)
            throw new AccessDeniedException("You are not a member of this conversation.");

        List<Map<String, Object>> messages = conversation.getMessages();
        if (messages == null || messages.isEmpty()) return;

        boolean isUpdated = false;

        // Duyệt ngược từ dưới lên (vì thường người ta đọc tin mới nhất)
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            String msgId = (String) msg.get("id");

            // Tìm đúng tin nhắn
            if (msgId != null && msgId.equals(request.getMessageId())) {

                // Lấy danh sách readBy hiện tại (hoặc tạo mới)
                List<Long> readBy = (List<Long>) msg.get("readBy");
                if (readBy == null) {
                    readBy = new ArrayList<>();
                    msg.put("readBy", readBy);
                }

                // Nếu chưa đọc thì thêm vào
                // Lưu ý: List trong JSONB convert ra có thể là Integer hoặc Long tùy Hibernate, cần ép kiểu cẩn thận
                boolean alreadyRead = readBy.stream().anyMatch(id -> id.toString().equals(userId.toString()));

                if (!alreadyRead) {
                    readBy.add(userId);
                    isUpdated = true;

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("type", "EVENT_READ");
                    payload.put("conversationId", conversation.getId());
                    payload.put("messageId", msgId);
                    payload.put("readerId", userId);
                    payload.put("timestamp", Instant.now().toString());

                    messagingTemplate.convertAndSend("/queue/conversation/" + conversation.getId(), payload);
                }
                break; // Tìm thấy rồi thì dừng
            }
        }

        // 4. Lưu DB nếu có thay đổi
        if (isUpdated) {
            conversationRepository.save(conversation);
        }
    }

    // -------------------------HELPER METHODS-------------------------
    private ConversationSummaryResponse toConversationSummaryDto(Conversation c, Long viewerId) {
        Map<String, Object> lastMessage = (c.getMessages() != null && !c.getMessages().isEmpty())
                ? c.getMessages().getFirst()
                : null;

        List<ParticipantDto> participants = c.getMembers().stream()
                .filter(m -> m.getUser() != null)
                .map(m -> ParticipantDto.builder()
                        .id(m.getUser().getId())
                        .displayName(m.getUser().getDisplayName())
                        .avatarUrl(m.getUser().getAvatarUrl())
                        .role(m.getRole() != null ? m.getRole().name() : "MEMBER")
                        .build())
                .toList();

        String finalTitle = c.getTitle();
        String finalMediaUrl = c.getMediaUrl();

        if (c.getIsGroup() == null || !c.getIsGroup()) {
            // Chat 1-1
            ParticipantDto otherUser = participants.stream()
                    .filter(p -> !p.getId().equals(viewerId))
                    .findFirst()
                    .orElse(null);

            if (otherUser != null) {
                finalTitle = otherUser.getDisplayName();
                finalMediaUrl = otherUser.getAvatarUrl();
            } else if (finalTitle == null) {
                finalTitle = "Conversation";
            }
        }

        return ConversationSummaryResponse.builder()
                .id(c.getId())
                .title(finalTitle)
                .mediaUrl(finalMediaUrl)
                .isGroup(c.getIsGroup() != null && c.getIsGroup())
                .lastMessage(lastMessage)
                .participants(participants)
                .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt())
                .build();
    }

    private ConversationMember checkGroupAndGetMember(Long conversationId, Long userId) {
        ConversationMember member = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this conversation."));

        Conversation convo = member.getConversation();
        if (convo == null) { // Đảm bảo convo được load
            convo = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
            member.setConversation(convo);
        }

        if (convo.getIsGroup() == null || !convo.getIsGroup()) {
            throw new BadRequestException("This action is only allowed in group conversations.");
        }
        return member;
    }

    private void saveAndSendSystemMessage(Conversation conversation, User sender, String content) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", UUID.randomUUID().toString());
        messageMap.put("senderId", sender.getId());
        messageMap.put("senderName", sender.getDisplayName());
        messageMap.put("content", content);
        messageMap.put("type", MessageType.SYSTEM.name());
        messageMap.put("createdAt", Instant.now().toString());

        if (conversation.getMessages() == null) {
            conversation.setMessages(new ArrayList<>());
        }
        conversation.getMessages().add(messageMap);

        conversationRepository.save(conversation);

        Map<String, Object> socketPayload = new HashMap<>(messageMap);
        socketPayload.put("conversationId", conversation.getId());

        socketPayload.put("senderName", sender.getDisplayName());
        socketPayload.put("avatarUrl", sender.getAvatarUrl());

        messagingTemplate.convertAndSend("/queue/conversation/" + conversation.getId(), socketPayload);
    }

    private void sendSocketEvent(Long conversationId, String type, Long senderId, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("conversationId", conversationId);
        payload.put("senderId", senderId);
        payload.put("content", content);
        payload.put("timestamp", Instant.now().toString());
        messagingTemplate.convertAndSend("/queue/conversation/" + conversationId, payload);
    }
}
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
            saveAndSendSystemMessage(saved, creator, creator.getDisplayName() + " đã tạo nhóm.");
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

        if (member.getRole() != ConversationRole.OWNER) {
            throw new AccessDeniedException("Chỉ chủ nhóm mới có quyền cập nhật thông tin.");
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

            // 🔥 LƯU SYSTEM MESSAGE: Cập nhật thông tin
            saveAndSendSystemMessage(conversation, member.getUser(),
                    member.getUser().getDisplayName() + " đã cập nhật thông tin nhóm.");
        }

        return toConversationSummaryDto(conversation, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse addMembersToGroup(Long currentUserId, AddMembersRequest request) {
        ConversationMember currentUserMember = checkGroupAndGetMember(request.getConversationId(), currentUserId);
        Conversation conversation = currentUserMember.getConversation();

        if (currentUserMember.getRole() == ConversationRole.MEMBER) {
            throw new AccessDeniedException("Chỉ chủ nhóm (hoặc phó nhóm) mới có quyền thêm thành viên.");
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

        // 🔥 LƯU SYSTEM MESSAGE: Thêm thành viên
        String addedNames = newUsers.stream().map(User::getDisplayName).collect(Collectors.joining(", "));
        saveAndSendSystemMessage(conversation, currentUserMember.getUser(),
                currentUserMember.getUser().getDisplayName() + " đã thêm " + addedNames + " vào nhóm.");

        Conversation updatedConvo = conversationRepository.findById(request.getConversationId()).get();
        return toConversationSummaryDto(updatedConvo, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse removeMemberFromGroup(Long currentUserId, Long conversationId, Long userIdToRemove) {
        ConversationMember currentUserMember = checkGroupAndGetMember(conversationId, currentUserId);
        ConversationMember targetMember = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userIdToRemove)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên này không tồn tại trong nhóm."));

        if (currentUserId.equals(userIdToRemove)) {
            throw new BadRequestException("Bạn không thể tự xóa mình. Hãy dùng chức năng 'Rời nhóm'.");
        }

        ConversationRole currentUserRole = currentUserMember.getRole();
        ConversationRole targetUserRole = targetMember.getRole();

        if (currentUserRole == ConversationRole.MEMBER) throw new AccessDeniedException("Chỉ chủ/phó nhóm mới được xóa.");
        if (targetUserRole == ConversationRole.OWNER) throw new AccessDeniedException("Không thể xóa chủ nhóm.");
        if (currentUserRole == ConversationRole.ADMIN && targetUserRole == ConversationRole.ADMIN)
            throw new AccessDeniedException("Phó nhóm không thể xóa phó nhóm khác.");

        String removedUserName = targetMember.getUser().getDisplayName();
        conversationMemberRepository.delete(targetMember);

        Conversation conversation = currentUserMember.getConversation();
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        // 🔥 LƯU SYSTEM MESSAGE: Xóa thành viên
        saveAndSendSystemMessage(conversation, currentUserMember.getUser(),
                currentUserMember.getUser().getDisplayName() + " đã xóa " + removedUserName + " khỏi nhóm.");

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
                throw new BadRequestException("Bạn là chủ nhóm. Hãy chuyển quyền trước khi rời.");
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

        saveAndSendSystemMessage(conversation, member.getUser(), leaverName + " đã rời nhóm.");
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

        String roleName = request.getNewRole() == ConversationRole.ADMIN ? "Phó nhóm" : "Thành viên";
        saveAndSendSystemMessage(currentUserMember.getConversation(), currentUserMember.getUser(),
                targetMember.getUser().getDisplayName() + " đã được phân quyền làm " + roleName + ".");

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

    // -------------------------HELPER METHODS-------------------------
    private ConversationSummaryResponse toConversationSummaryDto(Conversation c, Long viewerId) {
        Map<String, Object> lastMessage = (c.getMessages() != null && !c.getMessages().isEmpty())
                ? c.getMessages().getFirst()
                : null;

        List<ParticipantDto> participants = c.getMembers().stream()
                .map(ConversationMember::getUser)
                .filter(Objects::nonNull)
                .map(u -> ParticipantDto.builder()
                        .id(u.getId())
                        .displayName(u.getDisplayName())
                        .avatarUrl(u.getAvatarUrl())
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
                finalTitle = "Cuộc trò chuyện";
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
        // 1. Tạo Map Message (Cấu trúc JSON)
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", UUID.randomUUID().toString());
        messageMap.put("senderId", sender.getId());
        messageMap.put("senderName", sender.getDisplayName());
        messageMap.put("content", content);
        messageMap.put("type", MessageType.SYSTEM.name());
        messageMap.put("timestamp", Instant.now().toString());

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
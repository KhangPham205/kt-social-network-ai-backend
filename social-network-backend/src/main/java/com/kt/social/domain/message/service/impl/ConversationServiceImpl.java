package com.kt.social.domain.message.service.impl;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.domain.message.dto.*;
import com.kt.social.domain.message.enums.ConversationRole;
import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.model.ConversationMember;
import com.kt.social.domain.message.model.ConversationMemberId;
import com.kt.social.domain.message.repository.ConversationMemberRepository;
import com.kt.social.domain.message.repository.ConversationRepository;
import com.kt.social.domain.message.service.ConversationService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
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
    private final UserCredentialRepository credRepo;
    private final ConversationMemberRepository conversationMemberRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public ConversationResponse createConversation(ConversationCreateRequest req) {
        User creator = SecurityUtils.getCurrentUser(credRepo, userRepository);

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
        ConversationMember member = conversationMemberRepository.findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của cuộc hội thoại này."));

        Conversation conversation = member.getConversation();

        // Kiểm tra nghiệp vụ: Đây có phải là nhóm không?
        if (conversation.getIsGroup() == null || !conversation.getIsGroup()) {
            throw new BadRequestException("Chỉ có thể cập nhật thông tin cho chat nhóm.");
        }

        // Kiểm tra quyền: Bạn có phải là OWNER không?
//        if (member.getRole() != ConversationRole.OWNER) {
//            throw new AccessDeniedException("Chỉ chủ nhóm mới có quyền cập nhật thông tin.");
//        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            conversation.setTitle(request.getTitle());
        }

        if (request.getMediaFile() != null && !request.getMediaFile().isEmpty()) {
            // Xóa media cũ (nếu có)
            if (conversation.getMediaUrl() != null && !conversation.getMediaUrl().isBlank()) {
                storageService.deleteFile(conversation.getMediaUrl());
            }

            String newMediaUrl = storageService.saveFile(request.getMediaFile(), "conversations/" + conversation.getId());
            conversation.setMediaUrl(newMediaUrl);
        }

        conversation.setUpdatedAt(Instant.now());
        Conversation savedConversation = conversationRepository.save(conversation);

        return toConversationSummaryDto(savedConversation, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse addMembersToGroup(Long currentUserId, AddMembersRequest request) {

        ConversationMember currentUserMember = conversationMemberRepository.findByConversationIdAndUserId(request.getConversationId(), currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của cuộc hội thoại này."));

        Conversation conversation = currentUserMember.getConversation();

        if (conversation.getIsGroup() == null || !conversation.getIsGroup()) {
            throw new BadRequestException("Chỉ có thể thêm thành viên vào chat nhóm.");
        }

        // Kiểm tra quyền: Bạn có phải là OWNER/ADMIN không?
//        if (currentUserMember.getRole() == ConversationRole.MEMBER) {
//            throw new AccessDeniedException("Chỉ chủ nhóm (hoặc phó nhóm) mới có quyền thêm thành viên.");
//        }

        // Lấy danh sách ID các thành viên HIỆN TẠI (để lọc trùng)
        Set<Long> existingMemberIds = conversation.getMembers().stream()
                .map(cm -> cm.getUser().getId())
                .collect(Collectors.toSet());

        // Lọc ra các ID thực sự MỚI
        List<Long> newMemberIds = request.getUserIds().stream()
                .filter(id -> !existingMemberIds.contains(id))
                .distinct()
                .toList();

        if (newMemberIds.isEmpty()) {
            return toConversationSummaryDto(conversation, currentUserId);
        }

        List<User> newUsers = userRepository.findByIdIn(newMemberIds);
        if (newUsers.size() != newMemberIds.size()) {
            throw new ResourceNotFoundException("Một số user ID không hợp lệ.");
        }

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

        Conversation updatedConvo = conversationRepository.findById(request.getConversationId()).get();
        return toConversationSummaryDto(updatedConvo, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse removeMemberFromGroup(Long currentUserId, Long conversationId, Long userIdToRemove) {
        // 1. Lấy member thực hiện (và kiểm tra nhóm)
        ConversationMember currentUserMember = checkGroupAndGetMember(conversationId, currentUserId);

        // 2. Lấy member bị xóa
        ConversationMember targetMember = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userIdToRemove)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên này không tồn tại trong nhóm."));

        // 3. Kiểm tra logic: không thể tự xóa mình
        if (currentUserId.equals(userIdToRemove)) {
            throw new BadRequestException("Bạn không thể tự xóa mình. Hãy dùng chức năng 'Rời nhóm'.");
        }

        // 4. Kiểm tra quyền
        ConversationRole currentUserRole = currentUserMember.getRole();
        ConversationRole targetUserRole = targetMember.getRole();

        // Chỉ OWNER hoặc ADMIN mới có quyền xóa
        if (currentUserRole == ConversationRole.MEMBER) {
            throw new AccessDeniedException("Chỉ chủ nhóm hoặc phó nhóm mới có quyền xóa thành viên.");
        }

        // Không ai được xóa OWNER
        if (targetUserRole == ConversationRole.OWNER) {
            throw new AccessDeniedException("Không thể xóa chủ nhóm.");
        }

        // ADMIN không thể xóa ADMIN khác
        if (currentUserRole == ConversationRole.ADMIN && targetUserRole == ConversationRole.ADMIN) {
            throw new AccessDeniedException("Phó nhóm không thể xóa phó nhóm khác.");
        }

        // 5. Xóa
        conversationMemberRepository.delete(targetMember);

        // 6. Cập nhật thời gian và trả về
        Conversation conversation = currentUserMember.getConversation();
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        // Query lại để lấy danh sách member chính xác
        Conversation updatedConvo = conversationRepository.findById(conversationId).get();
        return toConversationSummaryDto(updatedConvo, currentUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse updateMemberRole(Long currentUserId, UpdateMemberRoleRequest request) {
        // Lấy member thực hiện (và kiểm tra nhóm)
        ConversationMember currentUserMember = checkGroupAndGetMember(request.getConversationId(), currentUserId);

        // Lấy member bị thay đổi
        ConversationMember targetMember = conversationMemberRepository.findByConversationIdAndUserId(request.getConversationId(), request.getUserIdToChange())
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên này không tồn tại trong nhóm."));

        // Validate vai trò mới (Không được phép gán OWNER)
        if (request.getNewRole() == null || request.getNewRole() == ConversationRole.OWNER) {
            throw new BadRequestException("Vai trò không hợp lệ. Chỉ có thể gán 'ADMIN' hoặc 'MEMBER'.");
        }

        // Kiểm tra quyền: Chỉ OWNER/ADMIN mới được làm
        if (currentUserMember.getRole() == ConversationRole.MEMBER) {
            throw new AccessDeniedException("Chỉ chủ/phó nhóm mới có quyền thay đổi vai trò thành viên.");
        }

        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BadRequestException("Không thể thay đổi vai trò của chủ nhóm.");
        }

        targetMember.setRole(request.getNewRole());
        conversationMemberRepository.save(targetMember);

        Conversation conversation = currentUserMember.getConversation();
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        Conversation updatedConvo = conversationRepository.findById(request.getConversationId()).get();
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

        List<Long> memberIds = List.of(a.getId(), b.getId());
        ConversationResponse.builder()
                .id(saved.getId())
                .isGroup(saved.getIsGroup())
                .title(saved.getTitle())
                .mediaUrl(saved.getMediaUrl())
                .createdAt(saved.getCreatedAt())
                .memberIds(memberIds)
                .build();
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
                .orElseThrow(() -> new AccessDeniedException("Bạn không phải là thành viên của cuộc hội thoại này."));

        Conversation convo = member.getConversation();
        if (convo == null) { // Đảm bảo convo được load
            convo = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
            member.setConversation(convo);
        }

        if (convo.getIsGroup() == null || !convo.getIsGroup()) {
            throw new BadRequestException("Hành động này chỉ áp dụng cho chat nhóm.");
        }
        return member;
    }
}
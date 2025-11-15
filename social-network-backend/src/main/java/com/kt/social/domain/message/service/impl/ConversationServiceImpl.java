package com.kt.social.domain.message.service.impl;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.domain.message.dto.ConversationCreateRequest;
import com.kt.social.domain.message.dto.ConversationResponse;
import com.kt.social.domain.message.enums.ConversationRole;
import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.model.ConversationMember;
import com.kt.social.domain.message.model.ConversationMemberId;
import com.kt.social.domain.message.repository.ConversationMemberRepository;
import com.kt.social.domain.message.repository.ConversationRepository;
import com.kt.social.domain.message.service.ConversationService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Override
    @Transactional
    public ConversationResponse createConversation(ConversationCreateRequest req) {
        User creator = SecurityUtils.getCurrentUser(credRepo, userRepository);

        Conversation convo = Conversation.builder()
                .isGroup(Boolean.TRUE.equals(req.getIsGroup()))
                .title(req.getTitle())
                .mediaUrl(req.getMediaUrl())
                .messages(new ArrayList<>())
                .createdAt(Instant.now())
                .build();

        Conversation saved = conversationRepository.save(convo);

        // 🔹 Người tạo là OWNER
        ConversationMember owner = ConversationMember.builder()
                .id(new ConversationMemberId(saved.getId(), creator.getId()))
                .conversation(saved)
                .user(creator)
                .joinedAt(Instant.now())
                .role(ConversationRole.OWNER)
                .build();
        memberRepository.save(owner);

        // 🔹 Các thành viên khác là MEMBER
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
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserConversations(Long userId) {
        List<ConversationMember> joined = conversationMemberRepository.findByUserId(userId);
        return joined.stream().map(cm -> {
            Conversation c = cm.getConversation();
            Map<String,Object> lastMessage = (c.getMessages() != null && !c.getMessages().isEmpty())
                    ? c.getMessages().getFirst() // newest first
                    : null;
            List<Long> memberIds = c.getMembers().stream().map(m -> m.getUser().getId()).toList();
            return Map.of(
                    "conversationId", c.getId(),
                    "title", c.getTitle(),
                    "isGroup", c.getIsGroup(),
                    "lastMessage", lastMessage,
                    "memberIds", memberIds,
                    "createdAt", c.getCreatedAt()
            );
        }).toList();
    }

    @Override
    @Transactional
    public ConversationResponse findOrCreateDirectConversation(Long userAId, Long userBId) {
        // sort ids để ổn định key (không bắt buộc nhưng hợp lý)
        List<Long> ids = Stream.of(userAId, userBId).sorted().toList();

        Optional<Conversation> existing = conversationRepository.findDirectConversationBetween(ids);
        if (existing.isPresent()) {
            Conversation c = existing.get();
            // build response
            List<Long> memberIds = memberRepository.findByConversation(c)
                    .stream().map(cm -> cm.getUser().getId()).toList();

            return ConversationResponse.builder()
                    .id(c.getId())
                    .isGroup(c.getIsGroup())
                    .title(c.getTitle())
                    .mediaUrl(c.getMediaUrl())
                    .createdAt(c.getCreatedAt())
                    .memberIds(memberIds)
                    .build();
        }

        // Nếu chưa có => tạo conversation mới _không_ phụ thuộc vào SecurityUtils
        // Lấy user entities
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

        // Thêm 2 member với role OWNER / MEMBER (chọn 1 convention)
        ConversationMember cmA = ConversationMember.builder()
                .id(new ConversationMemberId(saved.getId(), a.getId()))
                .conversation(saved)
                .user(a)
                .joinedAt(Instant.now())
                .role(ConversationRole.OWNER) // hoặc MEMBER, tùy quy ước
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
        return ConversationResponse.builder()
                .id(saved.getId())
                .isGroup(saved.getIsGroup())
                .title(saved.getTitle())
                .mediaUrl(saved.getMediaUrl())
                .createdAt(saved.getCreatedAt())
                .memberIds(memberIds)
                .build();
    }
}
package com.kt.social.domain.message.service.impl;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.domain.message.mapper.MessageMapper;
import com.kt.social.domain.message.model.*;
import com.kt.social.domain.message.repository.*;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements com.kt.social.domain.message.service.MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageReceiptRepository receiptRepository;
    private final MessageMapper messageMapper;
    private final UserRepository userRepository;
    private final UserCredentialRepository credRepo;

    @Override
    @Transactional
    public MessageResponse sendMessage(MessageRequest req) {
        User sender = SecurityUtils.getCurrentUser(credRepo, userRepository);

        Conversation convo = conversationRepository.findById(req.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        Message msg = Message.builder()
                .conversation(convo)
                .sender(sender)
                .replyId(req.getReplyId())
                .content(req.getContent())
                .mediaUrl(req.getMediaUrl())
                .createdAt(Instant.now())
                .build();

        Message saved = messageRepository.save(msg);

        // Create receipts for members (unread)
        List<ConversationMember> members = memberRepository.findByConversation(convo);
        for (ConversationMember m : members) {
            // skip creating receipt for sender if you prefer
            MessageReceipt r = MessageReceipt.builder()
                    .message(saved)
                    .user(m.getUser())
                    .isRead(m.getUser().getId().equals(sender.getId()))
                    .readAt(m.getUser().getId().equals(sender.getId()) ? Instant.now() : null)
                    .build();
            receiptRepository.save(r);
        }

        return messageMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void markAsRead(Long messageId) {
        User current = SecurityUtils.getCurrentUser(credRepo, userRepository);
        MessageReceipt receipt = receiptRepository.findByMessageIdAndUserId(messageId, current.getId())
                .orElseThrow(() -> new RuntimeException("Receipt not found"));
        receipt.setIsRead(true);
        receipt.setReadAt(Instant.now());
        receiptRepository.save(receipt);
    }

    @Override
    public List<Long> getConversationMembers(Long conversationId) {
        return memberRepository.findByConversationId(conversationId)
                .stream().map(cm -> cm.getUser().getId()).collect(Collectors.toList());
    }

    @Override
    public Page<MessageResponse> getMessagesByConversation(Long conversationId, Pageable pageable) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        Page<Message> page = messageRepository.findByConversationOrderByCreatedAtDesc(conv, pageable);

        return page.map(messageMapper::toDto);
    }
}
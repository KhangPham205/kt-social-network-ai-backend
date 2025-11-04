package com.kt.social.domain.message.service;

import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.dto.MessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MessageService {
    MessageResponse sendMessage(MessageRequest request);
    void markAsRead(Long messageId);
    List<Long> getConversationMembers(Long conversationId);
    Page<MessageResponse> getMessagesByConversation(Long conversationId, Pageable pageable);

    MessageResponse sendMessageAs(Long userId, MessageRequest request);
}
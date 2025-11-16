package com.kt.social.domain.message.service;

import com.kt.social.domain.message.dto.ConversationCreateRequest;
import com.kt.social.domain.message.dto.ConversationResponse;

import java.util.List;
import java.util.Map;

public interface ConversationService {
    ConversationResponse createConversation(ConversationCreateRequest req);
    List<Map<String, Object>> getUserConversations(Long userId);
    void findOrCreateDirectConversation(Long userAId, Long userBId);
}
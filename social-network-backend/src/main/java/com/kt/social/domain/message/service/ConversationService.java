package com.kt.social.domain.message.service;

import com.kt.social.domain.message.dto.ConversationCreateRequest;
import com.kt.social.domain.message.dto.ConversationResponse;

public interface ConversationService {
    ConversationResponse createConversation(ConversationCreateRequest req);
}
package com.kt.social.domain.message.service;

import com.kt.social.domain.message.dto.MessageRequest;

import java.util.List;
import java.util.Map;

public interface MessageService {
    Map<String, Object> sendMessage(MessageRequest req);
    List<Map<String, Object>> getMessages(Long conversationId);

    void sendMessageAs(Long senderId, MessageRequest messageRequest);
}
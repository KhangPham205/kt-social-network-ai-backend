package com.kt.social.domain.moderation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

@Getter
public class MessageSentEvent extends ApplicationEvent {
    private final String id;
    private final Long conversationId;
    private final String content;
    private final Long senderId;
    private final Instant createdAt = Instant.now();

    public MessageSentEvent(Object source, String messageId, Long conversationId, String content, Long senderId) {
        super(source);
        this.id = messageId;
        this.conversationId = conversationId;
        this.content = content;
        this.senderId = senderId;
    }
}

package com.kt.social.domain.message.model;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberId implements Serializable {
    private Long conversationId;
    private Long userId;
}
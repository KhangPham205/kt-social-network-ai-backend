package com.kt.social.domain.message.model;

import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "conversation_member")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMember {
    @EmbeddedId
    private ConversationMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    private Instant joinedAt;
    private String role;
}
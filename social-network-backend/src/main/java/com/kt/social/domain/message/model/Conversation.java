package com.kt.social.domain.message.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Boolean isGroup = false;
    private String title;
    private String mediaUrl;
    private Instant createdAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ConversationMember> members;
}
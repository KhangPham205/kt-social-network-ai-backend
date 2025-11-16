package com.kt.social.domain.message.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ConversationMember> members;

    /**
     * 🔹 Lưu tất cả message dạng JSONB
     * Cấu trúc:
     * [
     *   {
     *     "id": "uuid",
     *     "senderId": 3,
     *     "content": "Hello",
     *     "media": [ {"url": "...", "type": "image"} ],
     *     "timestamp": "2025-11-08T15:24:12Z"
     *   },
     *   ...
     * ]
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> messages;
}
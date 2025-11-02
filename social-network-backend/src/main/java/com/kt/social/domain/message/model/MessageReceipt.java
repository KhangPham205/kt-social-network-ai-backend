package com.kt.social.domain.message.model;

import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "message_receipt")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Boolean isRead = false;
    private Instant readAt;
}
package com.kt.social.domain.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NotificationDto {
    private Long id;
    private ActorDto actor;
    private String content;
    private String link;
    private boolean isRead;
    private Instant createdAt;
}

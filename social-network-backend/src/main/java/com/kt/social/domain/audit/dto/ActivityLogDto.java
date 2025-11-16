package com.kt.social.domain.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class ActivityLogDto {
    private Long id;
    private Long actorId;
    private String action;
    private String targetResourceType;
    private Long targetResourceId;
    private Map<String, Object> details;
    private Instant createdAt;
}

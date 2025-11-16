package com.kt.social.domain.message.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParticipantDto {
    private Long id;
    private String displayName;
    private String avatarUrl;
}
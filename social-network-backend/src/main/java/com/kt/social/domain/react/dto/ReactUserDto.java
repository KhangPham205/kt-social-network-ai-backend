package com.kt.social.domain.react.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactUserDto {
    private Long userId;
    private String displayName;
    private String avatarUrl;
    private Long reactTypeId;
    private String reactTypeName; // ví dụ: LIKE, LOVE, HAHA
}
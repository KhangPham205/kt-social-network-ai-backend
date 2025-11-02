package com.kt.social.domain.comment.dto;

import lombok.*;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private Long id;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private String content;
    private String imageUrl;
    private Instant createdAt;
    private Instant updatedAt;

    private Long parentId; // để biết là reply của ai
    private Integer depth;
    private List<CommentResponse> children; // phản hồi con
}
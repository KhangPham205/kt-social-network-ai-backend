package com.kt.social.domain.post.dto;

import com.kt.social.domain.post.enums.AccessScope;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {
    private Long id;
    private String content;
    private String mediaUrl;
    private AccessScope accessModifier;

    private int likeCount;
    private int commentCount;
    private int shareCount;

    private String authorName;
    private String authorAvatar;
    private Long authorId;

    // Nếu bài gốc không khả dụng => null
    private PostResponse sharedPost;

    private Instant createdAt;
    private Instant updatedAt;
}
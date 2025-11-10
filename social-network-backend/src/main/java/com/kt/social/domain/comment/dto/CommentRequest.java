package com.kt.social.domain.comment.dto;

import lombok.Data;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CommentRequest {
    private Long postId;

    @Nullable
    private Long parentId;

    private String content;
    private MultipartFile mediaFile;
}
package com.kt.social.domain.comment.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CommentRequest {
    private Long postId;
    private Long parentId;
    private String content;
    private MultipartFile mediaFile;
}
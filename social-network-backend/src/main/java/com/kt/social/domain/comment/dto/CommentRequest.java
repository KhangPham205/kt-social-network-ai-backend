package com.kt.social.domain.comment.dto;

import lombok.Data;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class CommentRequest {
    private Long postId;

    @Nullable
    private Long parentId;

    private String content;
    private List<MultipartFile> mediaFiles;
}
package com.kt.social.domain.post.dto;

import com.kt.social.domain.post.enums.AccessScope;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class PostRequest {
    private String content;
    private AccessScope accessModifier;
    private Long sharedPostId;
    private MultipartFile media;
}
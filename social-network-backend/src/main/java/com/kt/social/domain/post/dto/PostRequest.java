package com.kt.social.domain.post.dto;

import com.kt.social.domain.post.enums.AccessScope;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PostRequest {
    private Long postId;
    private String content;
    private AccessScope accessModifier;
    private Long sharedPostId;
    private List<Map<String, Object>> media;
}
package com.kt.social.domain.message.dto;

import lombok.Data;

import java.util.List;

@Data
public class ConversationCreateRequest {
    private Boolean isGroup;
    private String title;
    private String mediaUrl;
    private List<Long> memberIds; // danh sách id user trong nhóm
}
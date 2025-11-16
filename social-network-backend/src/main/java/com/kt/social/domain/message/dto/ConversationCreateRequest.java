package com.kt.social.domain.message.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class ConversationCreateRequest {
    private Boolean isGroup;
    private String title;
    private MultipartFile media;
    private List<Long> memberIds; // danh sách id user trong nhóm
}
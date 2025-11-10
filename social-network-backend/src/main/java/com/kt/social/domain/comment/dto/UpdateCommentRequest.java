package com.kt.social.domain.comment.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateCommentRequest {
    private Long commentId;
    private String content;
    private MultipartFile mediaFile; // Ảnh mới (nếu có)
    private Boolean removeMedia;     // Nếu true thì xóa ảnh hiện tại
}
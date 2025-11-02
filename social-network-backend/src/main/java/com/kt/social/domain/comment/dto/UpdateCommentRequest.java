package com.kt.social.domain.comment.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateCommentRequest {
    private Long commentId;
    private String content;
    private MultipartFile imageFile; // Ảnh mới (nếu có)
    private Boolean removeImage;     // Nếu true thì xóa ảnh hiện tại
}
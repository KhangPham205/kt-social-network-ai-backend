package com.kt.social.domain.comment.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class UpdateCommentRequest {
    private Long commentId;
    private String content;
    private List<MultipartFile> mediaFiles; // Ảnh mới (nếu có)
    private Boolean removeMedia;     // Nếu true thì xóa ảnh hiện tại
}
package com.kt.social.domain.post.dto;

import com.kt.social.domain.post.enums.AccessScope;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCommentRequest {
    private Long postId;
    private String content;
    private AccessScope accessModifier;
    private List<String> keepMediaUrls;
    private List<String> removeMediaUrls;
    private List<MultipartFile> mediaFiles;
}

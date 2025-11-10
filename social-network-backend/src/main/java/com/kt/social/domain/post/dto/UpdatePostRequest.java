package com.kt.social.domain.post.dto;

import com.kt.social.domain.post.enums.AccessScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePostRequest {
    private Long postId;
    private String content;
    private String accessModifier;
    private List<String> keepMediaUrls;
    private List<String> removeMediaUrls;

    @Nullable
    private List<MultipartFile> mediaFiles;
}

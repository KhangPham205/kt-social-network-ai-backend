package com.kt.social.domain.comment.dto;

import com.kt.social.domain.react.dto.ReactSummaryDto;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private Long id;

    private Long authorId;
    private String authorName;
    private String authorAvatar;

    private ReactSummaryDto reactSummary;

    private Long postId;
    private Long parentId; // để biết là reply của ai

    private String content;
    private List<Map<String, String>> media; // thay cho mediaUrl

    private Integer reactCount;
    private Integer childrenCount;

    private Instant createdAt;
    private Instant updatedAt;

    private Integer depth;
//    private List<CommentResponse> children; // phản hồi con
}
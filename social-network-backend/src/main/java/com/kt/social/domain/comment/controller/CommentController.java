package com.kt.social.domain.comment.controller;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.comment.dto.CommentRequest;
import com.kt.social.domain.comment.dto.CommentResponse;
import com.kt.social.domain.comment.dto.UpdateCommentRequest;
import com.kt.social.domain.comment.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/post/{postId}")
    public ResponseEntity<PageVO<CommentResponse>> getCommentsByPost(
            @PathVariable Long postId,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(commentService.getCommentsByPost(postId, pageable));
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<PageVO<CommentResponse>> getReplies(
            @PathVariable Long commentId,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(commentService.getReplies(commentId, pageable));
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommentResponse> createComment(@ModelAttribute CommentRequest request) {
        return ResponseEntity.ok(commentService.createComment(request));
    }

    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommentResponse> updateComment(@ModelAttribute UpdateCommentRequest request) {
        return ResponseEntity.ok(commentService.updateComment(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        commentService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }
}
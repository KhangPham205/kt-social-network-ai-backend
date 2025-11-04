package com.kt.social.domain.comment.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.comment.dto.CommentRequest;
import com.kt.social.domain.comment.dto.CommentResponse;
import com.kt.social.domain.comment.dto.UpdateCommentRequest;
import org.springframework.data.domain.Pageable;

public interface CommentService {
    CommentResponse createComment(CommentRequest request);
    CommentResponse updateComment(UpdateCommentRequest request);
    PageVO<CommentResponse> getCommentsByPost(Long postId, Pageable pageable);
    PageVO<CommentResponse> getReplies(Long parentId, Pageable pageable);
    void deleteComment(Long id);
}
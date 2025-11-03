package com.kt.social.domain.post.controller;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.post.dto.PostRequest;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping("/feed")
    public ResponseEntity<PageVO<PostResponse>> getFeed(
            @ParameterObject Pageable pageable,
            @RequestParam(required = false) String filter
    ) {
        return ResponseEntity.ok(postService.getFeed(pageable, filter));
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> create(@ModelAttribute PostRequest request) {
        return ResponseEntity.ok(postService.create(request));
    }

    @PostMapping(value = "/share", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> sharePost(
            @RequestParam Long originalPostId,
            @RequestParam(required = false) String caption
    ) {
        return ResponseEntity.ok(postService.sharePost(originalPostId, caption));
    }

    @GetMapping("/me")
    public ResponseEntity<PageVO<PostResponse>> getMyPosts(
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(postService.getMyPosts(pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PageVO<PostResponse>> getUserPosts(
            @PathVariable Long userId,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(postService.getUserPosts(userId, pageable));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId) {
        postService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }
}
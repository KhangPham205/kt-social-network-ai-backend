package com.kt.social.domain.post.controller;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.post.dto.PostRequest;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.service.PostService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
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

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPostById(postId));
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> create(
            @RequestPart("content") String content,
            @RequestPart(value = "accessModifier", required = false) String accessModifier,

            @Parameter(schema = @Schema(type = "string", format = "binary"))
            @RequestPart(value = "media", required = false) List<MultipartFile> mediaFiles
    ) {
        return ResponseEntity.ok(
                postService.create(content, accessModifier, mediaFiles)
        );
    }

    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> update(
            @RequestPart("postId") Long postId,
            @RequestPart(value = "content", required = false) String content,
            @RequestPart(value = "accessModifier", required = false) String accessModifier,
            @RequestPart(value = "keepMediaUrls", required = false) List<String> keepMediaUrls,
            @RequestPart(value = "removeMediaUrls", required = false) List<String> removeMediaUrls,

            @Parameter(schema = @Schema(type = "string", format = "binary"))
            @RequestPart(value = "media", required = false) List<MultipartFile> mediaFiles
    ) {
        return ResponseEntity.ok(postService.update(
                postId,
                content,
                accessModifier,
                keepMediaUrls,
                removeMediaUrls,
                mediaFiles
        ));
    }

    @PostMapping("/share")
    public ResponseEntity<PostResponse> sharePost(
            @RequestParam Long originalPostId,
            @RequestParam(required = false) String caption
    ) {
        return ResponseEntity.ok(postService.sharePost(originalPostId, caption));
    }

    @GetMapping("/me")
    public ResponseEntity<PageVO<PostResponse>> getMyPosts(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(postService.getMyPosts(pageable));
    }

    @GetMapping("/user/{userId}")
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
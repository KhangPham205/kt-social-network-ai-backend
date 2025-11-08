package com.kt.social.domain.post.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.post.dto.PostRequest;
import com.kt.social.domain.post.dto.PostResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PostService {
    PostResponse create(String content, String accessModifier, Long sharedPostId, List<MultipartFile> mediaFiles);
    PostResponse update(Long postId, String content, String accessModifier, List<String> keepMediaUrls, List<String> removeMediaUrls, List<MultipartFile> mediaFiles);
    PostResponse getPostById(Long postId);
    PageVO<PostResponse> getMyPosts(Pageable pageable);
    PageVO<PostResponse> getUserPosts(Long userId, Pageable pageable);
    PostResponse sharePost(Long originalPostId, String caption);
    PageVO<PostResponse> getFeed(Pageable pageable, String filter);

    @Transactional
    void deletePost(Long postId);

}
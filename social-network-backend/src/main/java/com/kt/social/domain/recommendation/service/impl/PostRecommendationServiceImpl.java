package com.kt.social.domain.recommendation.service.impl;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.post.service.impl.PostServiceImpl;
import com.kt.social.domain.react.repository.ReactRepository;
import com.kt.social.domain.recommendation.service.PostRecommendationService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.milvus.MilvusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostRecommendationServiceImpl implements PostRecommendationService {

    private final ReactRepository reactRepository;
    private final MilvusService milvusService;
    private final PostRepository postRepository;
    private final UserService userService;
    private final PostServiceImpl postService;

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getExploreFeed(int page, int size) {
        User currentUser = userService.getCurrentUser();
        Long userId = currentUser.getId();

        List<Long> recommendedPostIds;

        // 1. Lấy 10 bài viết gần nhất user đã Like
        List<Long> likedPostIds = reactRepository.findLatestLikedPostIds(userId, PageRequest.of(0, 10));

        if (likedPostIds.isEmpty()) {
            log.info("User {} chưa like bài nào -> Cold Start (Lấy bài mới nhất)", userId);
            // Cold Start: Trả về bài mới nhất từ DB
            Page<Post> pageResult = postRepository.findAll(PageRequest.of(page, size));
            return postService.getPostResponsePageVO(currentUser, pageResult);
        }

        try {
            // 2. Lấy vector của các bài đã like
            List<List<Float>> likedVectors = milvusService.getVectorsByPostIds(likedPostIds);

            // 3. Tính Vector trung bình (User Persona)
            List<Float> userInterestVector = calculateMeanVector(likedVectors, 384);

            // 4. Tìm kiếm bài viết tương đồng (Semantic Search)
            // Lấy nhiều hơn size một chút để trừ hao bài trùng hoặc bài đã xem
            recommendedPostIds = milvusService.searchSimilarPosts(userInterestVector, size * 2);

            // Lọc bỏ những bài chính user đã like (để không gợi ý lại bài cũ)
            recommendedPostIds.removeAll(likedPostIds);

        } catch (Exception e) {
            log.error("Lỗi AI Recommendation: {}", e.getMessage());
            // Fallback về bài mới nhất nếu lỗi
            Page<Post> pageResult = postRepository.findAll(PageRequest.of(page, size));
            return postService.getPostResponsePageVO(currentUser, pageResult);
        }

        if (recommendedPostIds.isEmpty()) {
            Page<Post> pageResult = postRepository.findAll(PageRequest.of(page, size));
            return postService.getPostResponsePageVO(currentUser, pageResult);
        }

        // 5. Lấy nội dung bài viết từ DB theo danh sách ID gợi ý
        // Lưu ý: findAllById không đảm bảo thứ tự, cần sắp xếp lại theo thứ tự của recommendedPostIds
        List<Post> posts = postRepository.findAllById(recommendedPostIds);

        // Sắp xếp lại list posts theo thứ tự của recommendedPostIds (độ tương đồng giảm dần)
        List<Post> sortedPosts = new ArrayList<>();
        for (Long id : recommendedPostIds) {
            posts.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(sortedPosts::add);
        }

        // Tạo Page giả lập từ List
        Page<Post> postPage = new org.springframework.data.domain.PageImpl<>(sortedPosts, PageRequest.of(page, size), sortedPosts.size());

        // 6. Convert sang DTO (Dùng lại logic của PostService để có đủ info react/share)
        return postService.getPostResponsePageVO(currentUser, postPage);
    }

    /**
     * Thuật toán tính trung bình cộng các Vector
     */
    private List<Float> calculateMeanVector(List<List<Float>> vectors, int dimension) {
        if (vectors == null || vectors.isEmpty()) {
            return Collections.nCopies(dimension, 0.0f);
        }

        List<Float> meanVector = new ArrayList<>(Collections.nCopies(dimension, 0.0f));
        int n = vectors.size();

        for (List<Float> vec : vectors) {
            if (vec.size() != dimension) continue; // Skip vector lỗi
            for (int i = 0; i < dimension; i++) {
                meanVector.set(i, meanVector.get(i) + vec.get(i));
            }
        }

        for (int i = 0; i < dimension; i++) {
            meanVector.set(i, meanVector.get(i) / n);
        }

        return meanVector;
    }
}
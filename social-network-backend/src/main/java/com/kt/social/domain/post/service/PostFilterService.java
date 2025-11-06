package com.kt.social.domain.post.service;

import com.kt.social.common.service.BaseFilterService;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.enums.AccessScope;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.post.mapper.PostMapper;
import com.kt.social.domain.user.model.User;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostFilterService extends BaseFilterService<Post, PostResponse> {

    private final PostRepository postRepository;
    private final PostMapper postMapper;

    /**
     * Hàm dùng chung để filter & phân trang bài viết.
     */
    public PageVO<PostResponse> filterPosts(
            String filter,
            Pageable pageable,
            List<Long> authorIds
    ) {
        Specification<Post> baseSpec = (root, query, cb) -> cb.and(
                root.get("author").get("id").in(authorIds),
                cb.notEqual(root.get("accessModifier"), AccessScope.PRIVATE)
        );

        return filterEntity(
                Post.class,
                filter,
                pageable,
                postRepository,
                postMapper::toDto,
                baseSpec
        );
    }
}
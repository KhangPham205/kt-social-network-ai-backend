package com.kt.social.domain.post.repository;

import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    Page<Post> findByAuthor(User author, Pageable pageable);
    List<Post> findBySharedPost(Post sharedPost);

    Page<Post> findAll(Specification<Post> spec, Pageable pageable);
}
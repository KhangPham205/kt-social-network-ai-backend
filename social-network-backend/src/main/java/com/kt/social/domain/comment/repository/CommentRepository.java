package com.kt.social.domain.comment.repository;

import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.post.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPostAndParentIsNull(Post post, Pageable pageable);
    List<Comment> findByParent(Comment parent);

    List<Comment> findByPost(Post post);
}
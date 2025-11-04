package com.kt.social.domain.comment.repository;

import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.post.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPostAndParentIsNull(Post post, Pageable pageable);
    Page<Comment> findByParent(Comment parent, Pageable pageable);
    int countByParent(Comment parent);
    List<Comment> findByPost(Post post); // nếu cần dùng cho lazy prefetch
}
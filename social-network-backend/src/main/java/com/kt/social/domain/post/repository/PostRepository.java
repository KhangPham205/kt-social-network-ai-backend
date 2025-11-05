package com.kt.social.domain.post.repository;

import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    Page<Post> findByAuthor(User author, Pageable pageable);
    List<Post> findBySharedPost(Post sharedPost);

    Page<Post> findAll(Specification<Post> spec, @NonNull Pageable pageable);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.sharedPost.id = :postId")
    int countSharesByPostId(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :postId")
    void updateCommentCount(@Param("postId") Long postId, @Param("delta") int delta);
}
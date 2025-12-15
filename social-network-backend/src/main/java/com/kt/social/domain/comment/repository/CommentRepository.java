package com.kt.social.domain.comment.repository;

import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.post.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPostAndParentIsNull(Post post, Pageable pageable);
    Page<Comment> findByParent(Comment parent, Pageable pageable);
    int countByParent(Comment parent);

    @Query("SELECT c.parent.id, COUNT(c.id) FROM Comment c WHERE c.parent.id IN :parentIds GROUP BY c.parent.id")
    List<Object[]> findChildrenCountsRaw(@Param("parentIds") List<Long> parentIds);

    // Helper để chuyển đổi
    default Map<Long, Integer> findChildrenCounts(List<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Map.of();
        }
        return findChildrenCountsRaw(parentIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue()
                ));
    }

    @Query(value = "SELECT * FROM comments WHERE id = :id", nativeQuery = true)
    Optional<Comment> findByIdIncludingDeleted(@Param("id") Long id);

    Page<Comment> findAll(Specification<Comment> spec, Pageable pageable);
}
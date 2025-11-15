package com.kt.social.domain.react.repository;

import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.model.React;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReactRepository extends JpaRepository<React, Long> {
    long countByTargetIdAndTargetType(Long targetId, TargetType targetType);
    Optional<React> findByUserAndTargetIdAndTargetType(User user, Long targetId, TargetType targetType);

    Page<React> findByTargetIdAndTargetType(Long targetId, TargetType targetType, Pageable pageable);

    @Query("""
        SELECT r.reactType.name AS typeName, COUNT(r) AS cnt
        FROM React r
        WHERE r.targetId = :targetId AND r.targetType = :targetType
        GROUP BY r.reactType.name
    """)
    List<Object[]> summarizeByTarget(@Param("targetId") Long targetId, @Param("targetType") TargetType targetType);


    @Query("SELECT r.reactType.name FROM React r WHERE r.user.id = :viewerId AND r.targetId = :targetId AND r.targetType = :targetType")
    Optional<String> findViewerReactNameForTarget(
            @Param("viewerId") Long viewerId,
            @Param("targetId") Long targetId,
            @Param("targetType") TargetType targetType
    );

    @Query("""
        SELECT r.targetId, r.reactType.name, COUNT(r)
        FROM React r
        WHERE r.targetId IN :targetIds AND r.targetType = :targetType
        GROUP BY r.targetId, r.reactType.name
    """)
    List<Object[]> summarizeByTargetList(@Param("targetIds") List<Long> targetIds, @Param("targetType") TargetType targetType);

    // (Dùng cho getReactSummaries cho N post - Query 2)
    @Query("""
        SELECT r.targetId, r.reactType.name
        FROM React r
        WHERE r.user.id = :viewerId AND r.targetId IN :targetIds AND r.targetType = :targetType
    """)
    List<Object[]> findViewerReactsForTargetList(
            @Param("viewerId") Long viewerId,
            @Param("targetIds") List<Long> targetIds,
            @Param("targetType") TargetType targetType
    );
}

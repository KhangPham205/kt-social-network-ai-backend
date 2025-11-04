package com.kt.social.domain.react.repository;

import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.model.React;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReactRepository extends JpaRepository<React, Long> {
    long countByTargetIdAndTargetType(Long targetId, TargetType targetType);
    Optional<React> findByUserAndTargetIdAndTargetType(User user, Long targetId, TargetType targetType);
    void deleteByUserAndTargetIdAndTargetType(User user, Long targetId, TargetType targetType);

    Page<React> findByTargetIdAndTargetType(Long targetId, TargetType targetType, Pageable pageable);
}

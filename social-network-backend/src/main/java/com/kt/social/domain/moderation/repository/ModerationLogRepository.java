package com.kt.social.domain.moderation.repository;

import com.kt.social.domain.moderation.model.ModerationLog;
import com.kt.social.domain.react.enums.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long>, JpaSpecificationExecutor<ModerationLog> {
    List<ModerationLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(TargetType targetType, String targetId);
}

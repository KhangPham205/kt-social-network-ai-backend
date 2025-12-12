package com.kt.social.domain.moderation.repository;

import com.kt.social.domain.moderation.model.ModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {
}

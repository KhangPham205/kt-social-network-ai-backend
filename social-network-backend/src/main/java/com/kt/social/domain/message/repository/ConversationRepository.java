package com.kt.social.domain.message.repository;

import com.kt.social.domain.message.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
        select c
        from Conversation c
        join c.members m
        where c.isGroup = false
          and m.user.id in :userIds
        group by c
        having count(distinct m.user.id) = 2
        """)
    Optional<Conversation> findDirectConversationBetween(@Param("userIds") List<Long> userIds);
}
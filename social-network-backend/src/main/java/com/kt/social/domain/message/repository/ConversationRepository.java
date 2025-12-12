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

    @Query(value = """
        SELECT * FROM conversations c
        WHERE EXISTS (
            SELECT 1
            FROM jsonb_array_elements(c.messages) AS msg
            WHERE msg->>'id' = :messageId
        )
    """, nativeQuery = true)
    Optional<Conversation> findByMessageIdInJson(@Param("messageId") String messageId);

    @Query("SELECT c FROM Conversation c " +
            "JOIN c.members m1 " +
            "JOIN c.members m2 " +
            "WHERE c.isGroup = false " +
            "AND m1.user.id = :userId1 " +
            "AND m2.user.id = :userId2")
    Optional<Conversation> findExistingPrivateConversation(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );
}
package com.kt.social.domain.message.repository;

import com.kt.social.domain.moderation.dto.FlaggedMessageProjection;
import com.kt.social.domain.moderation.dto.ModerationMessageResponse;
import com.kt.social.domain.message.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
    SELECT 
        CAST(msg ->> 'id' AS TEXT) as id,
        CAST(c.id AS BIGINT) as conversationId,
        c.title as conversationTitle,
        CAST(c.is_group AS BOOLEAN) as isGroup,
        CAST(msg ->> 'senderId' AS BIGINT) as senderId,
        msg ->> 'senderName' as senderName,
        msg ->> 'senderAvatar' as senderAvatar,
        msg ->> 'content' as content,
        msg ->> 'createdAt' as sentAt,
        CAST(msg ->> 'deletedAt' AS TIMESTAMP) as deletedAt,
        CAST(msg -> 'media' AS TEXT) as media
    FROM conversations c,
         jsonb_array_elements(c.messages) msg
    WHERE 
    (
        -- Điều kiện 1: Đã bị xóa (System ban hoặc delete)
        (msg ->> 'deletedAt' IS NOT NULL)
        OR 
        -- Điều kiện 2: Có nằm trong bảng Report
        EXISTS (
            SELECT 1 FROM reports r 
            WHERE r.target_type = 'MESSAGE' 
            AND r.target_id = (msg ->> 'id')
        )
    )
    AND (:filter IS NULL OR msg ->> 'content' ILIKE %:filter%)
""",
            countQuery = """
    SELECT count(*)
    FROM conversations c, jsonb_array_elements(c.messages) msg
    WHERE 
    (
        (msg ->> 'deletedAt' IS NOT NULL)
        OR 
        EXISTS (
            SELECT 1 FROM reports r 
            WHERE r.target_type = 'MESSAGE' 
            AND r.target_id = (msg ->> 'id')
        )
    )
    AND (:filter IS NULL OR msg ->> 'content' ILIKE %:filter%)
""",
            nativeQuery = true)
    Page<FlaggedMessageProjection> findFlaggedMessages(@Param("filter") String filter, Pageable pageable);

    @Query(value = """
        SELECT * FROM conversations c
        WHERE EXISTS (
            SELECT 1
            FROM jsonb_array_elements(c.messages) AS msg
            WHERE 
                (msg ->> 'deletedAt' IS NOT NULL OR (msg ->> 'isDeleted')::boolean = true)
                OR 
                EXISTS (
                    SELECT 1 FROM reports r 
                    WHERE r.target_type = 'MESSAGE' 
                    AND r.target_id = (msg ->> 'id')
                )
        )
        ORDER BY c.updated_at DESC
    """,
            countQuery = """
        SELECT count(*) FROM conversations c
        WHERE EXISTS (
            SELECT 1
            FROM jsonb_array_elements(c.messages) AS msg
            WHERE 
                (msg ->> 'deletedAt' IS NOT NULL OR (msg ->> 'isDeleted')::boolean = true)
                OR 
                EXISTS (
                    SELECT 1 FROM reports r 
                    WHERE r.target_type = 'MESSAGE' 
                    AND r.target_id = (msg ->> 'id')
                )
        )
    """,
            nativeQuery = true)
    Page<Conversation> findConversationsWithFlaggedMessages(Pageable pageable);
}
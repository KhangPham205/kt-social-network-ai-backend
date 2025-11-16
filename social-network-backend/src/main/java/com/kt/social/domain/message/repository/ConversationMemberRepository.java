package com.kt.social.domain.message.repository;

import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.model.ConversationMember;
import com.kt.social.domain.message.model.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {
    List<ConversationMember> findByConversation(Conversation conversation);
    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("SELECT cm FROM ConversationMember cm " +
            "JOIN FETCH cm.conversation c " +
            "JOIN FETCH c.members m " +      // Lấy tất cả member của convo đó
            "JOIN FETCH m.user " +           // Lấy profile user của các member đó
            "WHERE cm.user.id = :userId " +
            "ORDER BY c.updatedAt DESC")     // Sắp xếp theo tin nhắn mới nhất
    List<ConversationMember> findConversationsByUserId(@Param("userId") Long userId);
}
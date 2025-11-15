package com.kt.social.domain.message.repository;

import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.model.ConversationMember;
import com.kt.social.domain.message.model.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {
    List<ConversationMember> findByConversation(Conversation conversation);
    List<ConversationMember> findByConversationId(Long conversationId);
    List<ConversationMember> findByUserId(Long userId);
}
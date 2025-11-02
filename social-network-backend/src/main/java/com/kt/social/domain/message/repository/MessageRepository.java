package com.kt.social.domain.message.repository;

import com.kt.social.domain.message.model.Message;
import com.kt.social.domain.message.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversationOrderByCreatedAtDesc(Conversation conversation, Pageable pageable);
}
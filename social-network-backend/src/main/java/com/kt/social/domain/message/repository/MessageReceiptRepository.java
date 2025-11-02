package com.kt.social.domain.message.repository;

import com.kt.social.domain.message.model.MessageReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, Long> {
    Optional<MessageReceipt> findByMessageIdAndUserId(Long messageId, Long userId);
}
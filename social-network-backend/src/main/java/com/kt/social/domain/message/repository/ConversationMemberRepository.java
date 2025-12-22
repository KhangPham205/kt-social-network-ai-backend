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

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    // Tìm member trong 1 group cụ thể
    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    // Đếm số thành viên
    long countByConversationId(Long conversationId);

    // Kiểm tra tồn tại
    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    // Lấy danh sách ID thành viên
    List<ConversationMember> findByConversationId(Long conversationId);

    @Query("SELECT cm FROM ConversationMember cm " +
            "JOIN FETCH cm.conversation c " +
            "LEFT JOIN FETCH c.members m " +  // Fetch luôn members của conversation để hiển thị avatar
            "LEFT JOIN FETCH m.user u " +     // Fetch user của members
            "WHERE cm.user.id = :userId")
    List<ConversationMember> findConversationsByUserId(@Param("userId") Long userId);
}
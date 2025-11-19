package com.kt.social.domain.message.service;

import com.kt.social.domain.message.dto.*;
import com.kt.social.domain.message.enums.ConversationRole;

import java.util.List;

public interface ConversationService {
    ConversationResponse createConversation(ConversationCreateRequest req);
    ConversationSummaryResponse updateConversation(Long currentUserId, UpdateConversationRequest request);
    ConversationSummaryResponse addMembersToGroup(Long currentUserId, AddMembersRequest request);
    ConversationSummaryResponse removeMemberFromGroup(Long currentUserId, Long conversationId, Long userIdToRemove);
    void leaveConversation(Long currentUserId, Long conversationId);
    ConversationSummaryResponse updateMemberRole(Long currentUserId, UpdateMemberRoleRequest request);
    List<ConversationSummaryResponse> getUserConversations(Long userId);
    ConversationSummaryResponse getConversationById(Long currentUserId, Long conversationId);
    void findOrCreateDirectConversation(Long userAId, Long userBId);

    void markMessageAsRead(Long userId, MarkReadRequest request);
}
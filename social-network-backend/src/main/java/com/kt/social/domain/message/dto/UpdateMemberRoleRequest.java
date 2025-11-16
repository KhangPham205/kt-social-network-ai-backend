package com.kt.social.domain.message.dto;

import com.kt.social.domain.message.enums.ConversationRole;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
    private Long conversationId;
    private Long userIdToChange;
    private ConversationRole newRole;
}

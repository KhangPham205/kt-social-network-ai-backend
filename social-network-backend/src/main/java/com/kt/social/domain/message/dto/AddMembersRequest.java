package com.kt.social.domain.message.dto;

import lombok.Data;

import java.util.List;

@Data
public class AddMembersRequest {
    private Long conversationId;
    private List<Long> userIds;
}

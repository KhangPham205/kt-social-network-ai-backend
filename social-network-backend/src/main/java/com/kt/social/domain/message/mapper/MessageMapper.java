package com.kt.social.domain.message.mapper;

import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.domain.message.model.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {

    @Mapping(source = "sender.id", target = "senderId")
    @Mapping(source = "sender.displayName", target = "senderName")
    @Mapping(source = "sender.avatarUrl", target = "senderAvatar")
    @Mapping(source = "conversation.id", target = "conversationId")
    @Mapping(source = "replyId", target = "replyToId")
    MessageResponse toDto(Message message);
}
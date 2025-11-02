package com.kt.social.domain.message.mapper;

import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.domain.message.model.Message;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {

    @Mapping(source = "conversation.id", target = "conversationId")
    @Mapping(source = "sender.id", target = "senderId")
    @Mapping(source = "sender.displayName", target = "senderName")
    MessageResponse toDto(Message message);
}
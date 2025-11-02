package com.kt.social.domain.message.controller;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.domain.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<PageVO<MessageResponse>> getConversationMessages(
            @PathVariable Long conversationId,
            Pageable pageable
    ) {
        Page<MessageResponse> page = messageService.getMessagesByConversation(conversationId, pageable);

        PageVO<MessageResponse> result = PageVO.<MessageResponse>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(page.getNumberOfElements())
                .content(page.getContent())
                .build();

        return ResponseEntity.ok(result);
    }
}
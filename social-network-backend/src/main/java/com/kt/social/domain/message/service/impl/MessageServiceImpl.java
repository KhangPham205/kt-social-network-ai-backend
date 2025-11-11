package com.kt.social.domain.message.service.impl;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.repository.ConversationRepository;
import com.kt.social.domain.message.service.MessageService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ConversationRepository conversationRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final UserCredentialRepository credRepo;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Gửi tin nhắn từ HTTP (có file)
     */
    @Override
    @Transactional
    public Map<String, Object> sendMessage(MessageRequest req) {
        User sender = SecurityUtils.getCurrentUser(credRepo, userRepository);

        // Tạo tin nhắn (logic của bạn đã có)
        Map<String, Object> messageMap = createMessage(sender, req.getConversationId(), req.getContent(), req.getReplyToId(), req.getMediaFiles());

        // Phát tin nhắn tới kênh STOMP
        broadcastMessage(req.getConversationId(), messageMap);

        return messageMap;
    }

    /**
     * Gửi tin nhắn từ WebSocket (chỉ text)
     * (Đây là phương thức mới được gọi bởi ChatStompController)
     */
    @Override
    @Transactional
    public void sendMessageAs(Long senderId, MessageRequest req) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Tạo tin nhắn (không có file media)
        Map<String, Object> messageMap = createMessage(sender, req.getConversationId(), req.getContent(), req.getReplyToId(), null);

        // Phát tin nhắn tới kênh STOMP
        broadcastMessage(req.getConversationId(), messageMap);
    }

    /**
     * Logic chung để tạo và lưu tin nhắn
     */
    private Map<String, Object> createMessage(User sender, Long conversationId, String content, Long replyToId, List<org.springframework.web.multipart.MultipartFile> mediaFiles) {
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        List<Map<String, Object>> messages = convo.getMessages() != null
                ? new ArrayList<>(convo.getMessages())
                : new ArrayList<>();

        // Xử lý file (nếu có)
        List<Map<String, Object>> mediaList = new ArrayList<>();
        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            // (logic lưu file của bạn)
            for (var file : mediaFiles) {
                String url = storageService.saveFile(file, "messages");
                String type = "file"; // (logic xác định type của bạn)
                mediaList.add(Map.of("url", url, "type", type));
            }
        }

        // Tạo đối tượng message
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", UUID.randomUUID().toString());
        message.put("senderId", sender.getId());
        message.put("senderName", sender.getDisplayName());
        message.put("senderAvatar", sender.getAvatarUrl());
        message.put("content", content);
        message.put("replyToId", replyToId);
        message.put("media", mediaList);
        message.put("timestamp", Instant.now().toString());

        messages.addFirst(message); // Thêm vào đầu danh sách
        convo.setMessages(messages);
        conversationRepository.save(convo);

        return message;
    }

    /**
     * Logic chung để phát tin nhắn qua WebSocket
     */
    private void broadcastMessage(Long conversationId, Map<String, Object> messageMap) {
        // Định nghĩa kênh STOMP
        String destination = "/queue/conversation/" + conversationId;

        // Gửi tin nhắn
        // (Bạn nên chuyển 'messageMap' thành DTO, ví dụ 'MessageResponse')
        messagingTemplate.convertAndSend(destination, messageMap);
    }

    /**
     * Lấy danh sách tin nhắn (đã được sắp xếp mới nhất -> cũ nhất)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessages(Long conversationId) {
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        List<Map<String, Object>> messages = convo.getMessages();
        return messages != null ? messages : List.of();
    }
}
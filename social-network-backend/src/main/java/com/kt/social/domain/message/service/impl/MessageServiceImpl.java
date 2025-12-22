// MessageServiceImpl.java
package com.kt.social.domain.message.service.impl;

import com.kt.social.common.constants.WebSocketConstants;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.CursorPage;
import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.domain.message.model.Conversation;
import com.kt.social.domain.message.repository.ConversationRepository;
import com.kt.social.domain.moderation.event.MessageSentEvent;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.message.service.MessageService;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ApplicationEventPublisher eventPublisher;
    private final ConversationRepository conversationRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // lightweight lock per conversation to avoid concurrent list corruption
    private final Map<Long, Object> convoLocks = new ConcurrentHashMap<>();
    private final UserService userService;

    private Object getLock(Long convoId) {
        return convoLocks.computeIfAbsent(convoId, id -> new Object());
    }

    @Override
    @Transactional
    public Map<String, Object> sendMessage(MessageRequest req) {
        User sender = userService.getCurrentUser();
        return createAndSaveMessage(sender.getId(), sender.getDisplayName(), sender.getAvatarUrl(),
                req.getConversationId(), req.getContent(), req.getReplyToId(), req.getMediaFiles());
    }

    @Override
    @Transactional
    public void sendMessageAs(Long senderId, MessageRequest req) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Map<String,Object> m = createAndSaveMessage(sender.getId(), sender.getDisplayName(), sender.getAvatarUrl(),
                req.getConversationId(), req.getContent(), req.getReplyToId(), req.getMediaFiles());
    }

    private Map<String,Object> createAndSaveMessage(Long senderId, String senderName, String senderAvatar,
                                                    Long conversationId, String content, Long replyToId,
                                                    List<MultipartFile> mediaFiles) {
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // build media list
        List<Map<String,Object>> media = new ArrayList<>();
        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            for (var f : mediaFiles) {
                String url = storageService.saveFile(f, "messages");
                String type = guessMediaTypeFromFilename(f.getOriginalFilename()); // implement helper
                media.add(Map.of("url", url, "type", type));
            }
        }

        Map<String,Object> message = new LinkedHashMap<>();
        String msgId = UUID.randomUUID().toString();
        message.put("id", msgId);
        message.put("senderId", senderId);
        message.put("senderName", senderName);
        message.put("senderAvatar", senderAvatar);
        message.put("replyToId", replyToId);
        message.put("content", content);
        message.put("media", media);
        message.put("createdAt", Instant.now().toString());
        message.put("reactions", new ArrayList<>()); // initially empty
        message.put("isDeleted", false);
        List<Long> readBy = new ArrayList<>();
        readBy.add(senderId); // Người gửi coi như đã đọc tin của chính mình (tùy chọn)
        message.put("readBy", readBy);

        synchronized (getLock(conversationId)) {
            List<Map<String,Object>> messages = convo.getMessages() != null
                    ? new ArrayList<>(convo.getMessages())
                    : new ArrayList<>();

            // add at head -> newest first
            messages.addFirst(message);
            convo.setMessages(messages);
            convo.setUpdatedAt(Instant.now());
            conversationRepository.save(convo);
        }

        // broadcast via STOMP
        broadcastToConversationMembers(conversationId, message);

        // publish event for moderation logging
        if (content != null && !content.isBlank()) {
            eventPublisher.publishEvent(new MessageSentEvent(
                    this,
                    msgId,
                    conversationId,
                    content,
                    senderId,
                    media
            ));
        }

        return message;
    }

    private void broadcastToConversationMembers(Long conversationId, Map<String,Object> message) {
        // destination for conversation (topic) - clients can subscribe
        String topicDest = WebSocketConstants.CHAT_CONVERSATION_QUEUE + "/" + conversationId;
        messagingTemplate.convertAndSend(topicDest, message);

//        // per-user queue (if you want per-user delivery)
//        // get members:
//        List<Long> memberIds = memberRepository.findByConversationId(conversationId)
//                .stream().map(cm -> cm.getUser().getId()).toList();
//        for (Long uid : memberIds) {
//            messagingTemplate.convertAndSendToUser(uid.toString(), "/queue/messages", message);
//        }
    }

    // Helper to guess media type (image/video) – keep simple
    private String guessMediaTypeFromFilename(String name) {
        if (name == null) return "file";
        String low = name.toLowerCase();
        if (low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".webp") || low.endsWith(".gif"))
            return "image";
        if (low.endsWith(".mp4") || low.endsWith(".webm") || low.endsWith(".mov"))
            return "video";
        return "file";
    }

    // Cursor paging logic
    @Override
    @Transactional(readOnly = true)
    public CursorPage<MessageResponse> getMessagesCursor(Long conversationId, String beforeMessageId, int limit) {
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        List<Map<String,Object>> messages = convo.getMessages() != null ? convo.getMessages() : List.of();

        // messages stored newest first (index 0). We return a slice also newest-first.
        int startIndex = 0; // default newest page
        if (beforeMessageId != null && !beforeMessageId.isBlank()) {
            // find index of message with id == beforeMessageId
            int idx = -1;
            for (int i = 0; i < messages.size(); i++) {
                Object idObj = messages.get(i).get("id");
                if (beforeMessageId.equals(String.valueOf(idObj))) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                // if cursor not found, return empty page
                return new CursorPage<>(List.of(), null);
            }
            // We want messages *after* that index (older messages): because idx points to last message of previous page,
            // older ones are at indices > idx
            startIndex = idx + 1;
        }

        if (startIndex >= messages.size()) {
            return new CursorPage<>(List.of(), null);
        }

        int endIndexExclusive = Math.min(startIndex + limit, messages.size());
        List<Map<String,Object>> pageSlice = messages.subList(startIndex, endIndexExclusive);

        // map to MessageResponse
        List<MessageResponse> content = pageSlice.stream().map(m -> mapToDto(conversationId, m)).collect(Collectors.toList());

        String nextCursor = (endIndexExclusive < messages.size())
                ? String.valueOf(messages.get(endIndexExclusive - 1).get("id"))
                : null;

        return new CursorPage<>(content, nextCursor);
    }

    private MessageResponse mapToDto(Long conversationId, Map<String,Object> m) {
        MessageResponse r = new MessageResponse();
        r.setId(String.valueOf(m.get("id")));
        r.setConversationId(conversationId);
        r.setSenderId(m.get("senderId") == null ? null : Long.valueOf(String.valueOf(m.get("senderId"))));
        r.setSenderName((String) m.get("senderName"));
        r.setSenderAvatar((String) m.get("senderAvatar"));
        r.setReplyToId(m.get("replyToId")==null?null:Long.valueOf(String.valueOf(m.get("replyToId"))));
        r.setContent((String) m.get("content"));
        // media stored as List<Map<String,Object>>
        r.setMedia((List<Map<String, Object>>) m.getOrDefault("media", List.of()));
        r.setCreatedAt(m.get("createdAt") == null ? null : Instant.parse((String)m.get("createdAt")));
        r.setIsRead(Boolean.TRUE.equals(m.get("isRead")));
        r.setReactions((List<Map<String,Object>>) m.getOrDefault("reactions", List.of()));

        Object deletedAtObj = m.get("deletedAt");
        if (deletedAtObj != null) {
            r.setDeletedAt(Instant.parse(String.valueOf(deletedAtObj)));
        } else {
            r.setDeletedAt(null);
        }

        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessages(Long conversationId) {
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        return convo.getMessages() != null ? convo.getMessages() : List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserConversations(Long userId) {
        throw new UnsupportedOperationException("Use ConversationService.getUserConversations");
    }

    @Override
    @Transactional
    public void softDeleteMessage(String messageId) {
        // 1. Tìm Conversation
        Conversation convo = conversationRepository.findByMessageIdInJson(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found or already deleted"));

        Long convoId = convo.getId();

        // 2. Lock theo ID cuộc hội thoại để tránh race condition (khi sửa JSON List)
        synchronized (getLock(convoId)) {
            // Query lại data mới nhất trong lock (nếu transaction isolation level cho phép)

            List<Map<String, Object>> messages = convo.getMessages();
            boolean found = false;

            // Duyệt ngược từ cuối lên (thường tin nhắn cần block là tin mới nhất) để nhanh hơn
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, Object> msg = messages.get(i);
                // So sánh chuỗi an toàn
                if (messageId.equals(String.valueOf(msg.get("id")))) {

                    // Cập nhật trạng thái
                    msg.put("isDeleted", true);
                    msg.put("deletedAt", Instant.now().toString());
                    msg.put("isSystemBan", true);

                    found = true;
                    break;
                }
            }

            if (found) {
                convo.setMessages(messages); // Gán lại list đã sửa
                convo.setUpdatedAt(Instant.now());
                conversationRepository.save(convo);

                // 3. Gửi Socket
//                Map<String, Object> updatePayload = Map.of(
//                        "id", messageId,
//                        "conversationId", convoId,
//                        "content", "Tin nhắn đã bị gỡ bỏ.",
//                        "type", "MESSAGE_BLOCKED",
//                        "updatedAt", Instant.now().toString()
//                );
//                String topicDest = WebSocketConstants.CHAT_CONVERSATION_QUEUE + "/" + convoId;
//                messagingTemplate.convertAndSend(topicDest, updatePayload);

                convoLocks.remove(convoId);
            } else {
                throw new ResourceNotFoundException("Message ID found in DB query but missing in JSON parsing.");
            }
        }
    }
}
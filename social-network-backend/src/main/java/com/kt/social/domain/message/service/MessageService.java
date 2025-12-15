package com.kt.social.domain.message.service;

import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.common.vo.CursorPage;
import java.util.List;
import java.util.Map;

public interface MessageService {
    // HTTP multipart -> trả về message gửi thành công (map để giữ linh hoạt)
    Map<String, Object> sendMessage(MessageRequest req);

    // WS (text-only) hoặc send as user
    void sendMessageAs(Long senderId, MessageRequest req);

    // lấy page theo cursor (before = messageId), limit default 30
    CursorPage<MessageResponse> getMessagesCursor(Long conversationId, String beforeMessageId, int limit);

    // Lấy list messages toàn bộ (dùng hiếm) — trả về mới -> cũ
    List<Map<String,Object>> getMessages(Long conversationId);

    // Lấy conversation list cho user (kèm lastMessage preview)
    List<Map<String,Object>> getUserConversations(Long userId);

    // Xóa mềm message (chỉ ẩn với user, không xóa DB)
    void softDeleteMessage(String messageId);
}
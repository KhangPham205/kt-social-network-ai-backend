package com.kt.social.domain.notification.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.notification.dto.NotificationDto;
import com.kt.social.domain.notification.enums.NotificationType;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    /**
     * Tạo và gửi thông báo
     * @param actor Người thực hiện
     * @param receiver Người nhận
     * @param type Loại thông báo
     * @param targetId ID của đối tượng (Post, Comment)
     * @param postId ID của bài post gốc (để tạo link)
     */
    void sendNotification(User actor, User receiver, NotificationType type, Long targetId, Long postId);

    // (Các hàm API)
    PageVO<NotificationDto> getMyNotifications(User user, Pageable pageable);
    NotificationDto markAsRead(User user, Long notificationId);
    void markAllAsRead(User user);
}

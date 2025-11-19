package com.kt.social.domain.notification.service.impl;

import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.notification.dto.ActorDto;
import com.kt.social.domain.notification.dto.NotificationCountDto;
import com.kt.social.domain.notification.dto.NotificationDto;
import com.kt.social.domain.notification.enums.NotificationType;
import com.kt.social.domain.notification.model.Notification;
import com.kt.social.domain.notification.repository.NotificationRepository;
import com.kt.social.domain.notification.service.NotificationService;
import com.kt.social.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate; // <-- Để đẩy WebSocket

    @Override
    @Transactional
    public void sendNotification(User actor, User receiver, NotificationType type, Long targetId, Long postId) {

        if (actor.getId().equals(receiver.getId())) {
            return;
        }

        String content = "";
        String link = "";

        switch (type) {
            case REACT_POST:
                content = "đã thích bài viết của bạn.";
                link = "/posts/" + postId;
                break;
            case COMMENT_POST:
                content = "đã bình luận về bài viết của bạn.";
                link = "/posts/" + postId + "?comment=" + targetId;
                break;
            case REPLY_COMMENT:
                content = "đã trả lời bình luận của bạn.";
                link = "/posts/" + postId + "?reply=" + targetId;
                break;
            case REACT_COMMENT:
                content = "đã thích bình luận của bạn.";
                link = "/posts/" + postId + "?comment=" + targetId;
                break;
            case FRIEND_REQUEST:
                content = "đã gửi cho bạn một lời mời kết bạn.";
                link = "/users/" + actor.getId(); // Link tới profile người gửi
                break;
            case FRIEND_ACCEPT:
                content = "đã chấp nhận lời mời kết bạn của bạn.";
                link = "/users/" + actor.getId(); // Link tới profile người chấp nhận
                break;
        }

        Notification notification = Notification.builder()
                .receiver(receiver)
                .actor(actor)
                .type(type)
                .content(content)
                .link(link)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        NotificationDto dto = toDto(notification);

        messagingTemplate.convertAndSendToUser(
                receiver.getId().toString(),
                "/queue/notifications",
                dto
        );

        long unreadCount = notificationRepository.countByReceiverAndIsReadFalse(receiver);
        messagingTemplate.convertAndSendToUser(
                receiver.getId().toString(),
                "/queue/notification-summary",
                new NotificationCountDto(unreadCount)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<NotificationDto> getMyNotifications(User user, Pageable pageable) {
        // 1. Lấy trang (Page) từ repository
        Page<Notification> notificationPage = notificationRepository
                .findByReceiverOrderByCreatedAtDesc(user, pageable);

        // 2. Chuyển đổi (map) Page<Entity> sang List<DTO>
        List<NotificationDto> content = notificationPage.getContent().stream()
                .map(this::toDto) // Tái sử dụng helper 'toDto'
                .toList();

        // 3. Xây dựng và trả về PageVO
        return PageVO.<NotificationDto>builder()
                .page(notificationPage.getNumber())
                .size(notificationPage.getSize())
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    @Override
    @Transactional
    public NotificationDto markAsRead(User user, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getReceiver().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền đánh dấu thông báo này.");
        }

        // 3. Đánh dấu đã đọc (nếu chưa đọc)
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);

            // 4. Cập nhật (push) lại số lượng chưa đọc cho client
            long unreadCount = notificationRepository.countByReceiverAndIsReadFalse(user);
            messagingTemplate.convertAndSendToUser(
                    user.getId().toString(),
                    "/queue/notification-summary",
                    new NotificationCountDto(unreadCount)
            );
        }

        return toDto(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(User user) {
        // 1. Gọi query repository để cập nhật hàng loạt
        notificationRepository.markAllAsRead(user);

        // 2. Cập nhật (push) lại số lượng (là 0) cho client
        messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                "/queue/notification-summary",
                new NotificationCountDto(0L) // Gửi số 0
        );
    }

    /**
     * Helper để convert Entity -> DTO
     */
    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .actor(ActorDto.builder()
                        .id(n.getActor().getId())
                        .displayName(n.getActor().getDisplayName())
                        .avatarUrl(n.getActor().getAvatarUrl())
                        .build())
                .content(n.getContent())
                .link(n.getLink())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

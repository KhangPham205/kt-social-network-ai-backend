package com.kt.social.domain.notification.controller;

import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.notification.dto.NotificationDto;
import com.kt.social.domain.notification.service.NotificationService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.NOTIFICATIONS)
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    // 1. Lấy danh sách thông báo
    @GetMapping
    public ResponseEntity<PageVO<NotificationDto>> getMyNotifications(@ParameterObject Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(notificationService.getMyNotifications(currentUser, pageable));
    }

    // 2. Đánh dấu 1 thông báo là đã đọc
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(@PathVariable Long id) {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(notificationService.markAsRead(currentUser, id));
    }

    // 3. Đánh dấu TẤT CẢ là đã đọc
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        User currentUser = userService.getCurrentUser();
        notificationService.markAllAsRead(currentUser);
        return ResponseEntity.noContent().build();
    }
}
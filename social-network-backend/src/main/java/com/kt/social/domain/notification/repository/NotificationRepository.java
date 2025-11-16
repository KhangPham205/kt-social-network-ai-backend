package com.kt.social.domain.notification.repository;

import com.kt.social.domain.notification.model.Notification;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    // Tìm thông báo cho một user, sắp xếp mới nhất
    Page<Notification> findByReceiverOrderByCreatedAtDesc(User receiver, Pageable pageable);

    // Đếm số thông báo chưa đọc
    long countByReceiverAndIsReadFalse(User receiver);

    // Đánh dấu tất cả là đã đọc
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.receiver = :receiver AND n.isRead = false")
    void markAllAsRead(User receiver);
}

package com.example.hello.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.NotificationListResponse;
import com.example.hello.dto.NotificationResponse;
import com.example.hello.entity.Notification;
import com.example.hello.entity.User;
import com.example.hello.enums.NotificationTargetType;
import com.example.hello.enums.NotificationType;
import com.example.hello.repository.NotificationRepository;
import com.example.hello.repository.UserRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 查询通知列表（支持按类型和已读状态筛选）
     */
    public NotificationListResponse list(Long recipientId, NotificationType type, Boolean isRead, Pageable pageable) {
        Page<Notification> page;
        if (type != null && isRead != null) {
            page = notificationRepository.findByRecipientIdAndTypeAndIsReadOrderByCreatedAtDesc(recipientId, type, isRead, pageable);
        } else if (type != null) {
            page = notificationRepository.findByRecipientIdAndTypeOrderByCreatedAtDesc(recipientId, type, pageable);
        } else if (isRead != null) {
            page = notificationRepository.findByRecipientIdAndIsReadOrderByCreatedAtDesc(recipientId, isRead, pageable);
        } else {
            page = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        }

        List<NotificationResponse> notifications = page.getContent().stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());

        long unreadCount = notificationRepository.countByRecipientIdAndIsRead(recipientId, false);

        NotificationListResponse response = new NotificationListResponse();
        response.setNotifications(notifications);
        response.setUnreadCount(unreadCount);
        response.setTotalPages(page.getTotalPages());
        response.setTotalElements(page.getTotalElements());
        return response;
    }

    /**
     * 标记单条通知为已读
     */
    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("通知不存在"));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new RuntimeException("无权操作此通知");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    /**
     * 标记所有通知为已读
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    /**
     * 创建通知（供其他服务调用）
     */
    @Transactional
    public void createNotification(NotificationType type, String content, Long senderId,
                                   NotificationTargetType targetType, Long targetId, Long recipientId) {
        Notification notification = new Notification();
        notification.setType(type);
        notification.setContent(content);
        notification.setIsRead(false);

        if (senderId != null) {
            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("发送者不存在"));
            notification.setSender(sender);
        }

        notification.setTargetType(targetType);
        notification.setTargetId(targetId);

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("接收者不存在"));
        notification.setRecipient(recipient);

        notificationRepository.save(notification);
    }
}

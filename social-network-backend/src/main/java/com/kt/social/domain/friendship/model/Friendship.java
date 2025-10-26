package com.kt.social.domain.friendship.model;

import com.kt.social.common.entity.BaseEntity;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "friendship")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // người gửi yêu cầu

    @ManyToOne
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend; // người nhận yêu cầu

    @Enumerated(EnumType.STRING)
    private FriendshipStatus status;
}

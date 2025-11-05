package com.kt.social.domain.friendship.model;

import com.kt.social.common.entity.BaseEntity;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "friendship")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Friendship extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User sender; // người gửi yêu cầu

    @ManyToOne
    @JoinColumn(name = "friend_id", nullable = false)
    private User receiver; // người nhận yêu cầu

    @Enumerated(EnumType.STRING)
    private FriendshipStatus status;
}

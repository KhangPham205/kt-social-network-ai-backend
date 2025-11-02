package com.kt.social.domain.react.model;

import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "react",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "target_id", "target_type"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class React {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Người thực hiện
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Đối tượng tương tác (post, comment, message)
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_type", nullable = false)
    private TargetType targetType; // "POST", "COMMENT", "MESSAGE"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reaction_type_id", nullable = false)
    private ReactType reactType;

    private Instant createdAt;
}
package com.kt.social.domain.user.model;

import com.kt.social.auth.model.UserCredential;
import jakarta.persistence.*;
import lombok.*;
import com.kt.social.common.entity.BaseEntity;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User {

    @Id
    private Long id;

    @Column(nullable = false)
    private String displayName;

    private String avatarUrl;

    private String interestedUser;

    private Instant lastActiveAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserInfo userInfo;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @MapsId
    @JoinColumn(name = "id")
    private UserCredential credential;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

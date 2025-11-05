package com.kt.social.domain.user.model;

import com.kt.social.auth.model.UserCredential;
import jakarta.persistence.*;
import lombok.*;
import com.kt.social.common.entity.BaseEntity;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(nullable = false)
    private String displayName;

    private String avatarUrl;

    private String interestedUser;

    private Boolean isActive = true;

    @Column(nullable = false)
    private Boolean isOnline = false;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserInfo userInfo;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "credential_id", referencedColumnName = "id")
    private UserCredential credential;
}

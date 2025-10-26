package com.kt.social.domain.user.model;

import com.kt.social.auth.model.UserCredential;
import jakarta.persistence.*;
import lombok.*;
import com.kt.social.common.entity.BaseEntity;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false)
    private String displayName;

    private String avatarUrl;

    private String interestedUser;

    private Boolean isActive = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private UserInfo userInfo;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "credential_id", referencedColumnName = "id")
    private UserCredential credential;
}

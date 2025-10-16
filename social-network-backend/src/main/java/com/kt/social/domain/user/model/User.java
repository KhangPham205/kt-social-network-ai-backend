package com.kt.social.domain.user.model;

import com.kt.social.auth.model.UserCredential;
import jakarta.persistence.*;
import lombok.*;
import com.kt.social.common.entity.BaseEntity;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    private String email;

    private String avatarUrl;

    private String status;

    private Boolean isActive = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserInfo userInfo;

    @OneToOne(mappedBy = "user")
    private UserCredential credential;
}

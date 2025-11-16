package com.kt.social.domain.user.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfo {

    @Id
    private Long id;

    private String bio;

    private String favorites;

    private Instant dateOfBirth;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @CreationTimestamp
    private Instant createdAt;

    private String createdBy;

    @UpdateTimestamp
    private Instant updatedAt;

    private String updatedBy;
}
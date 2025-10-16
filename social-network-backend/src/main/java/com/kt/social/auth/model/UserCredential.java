package com.kt.social.auth.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_credential")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "USER";

    private boolean enabled = true;
}

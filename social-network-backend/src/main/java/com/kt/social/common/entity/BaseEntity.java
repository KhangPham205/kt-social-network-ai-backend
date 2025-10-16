package com.kt.social.common.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.springframework.lang.Nullable;

public class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nullable
    private Long createdAt;

    @Nullable
    private Long updatedAt;

    @Nullable
    private String createdBy;

    @Nullable
    private String updatedBy;
}

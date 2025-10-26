package com.kt.social.common.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.lang.Nullable;

@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@Accessors(chain = true)
@MappedSuperclass
@EqualsAndHashCode
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

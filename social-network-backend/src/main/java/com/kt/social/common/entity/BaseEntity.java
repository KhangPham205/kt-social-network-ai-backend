package com.kt.social.common.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.lang.Nullable;

import java.time.Instant;

@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@Accessors(chain = true)
@MappedSuperclass
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nullable
    private Instant createdAt;

    @Nullable
    private Instant updatedAt;

    @Nullable
    private String createdBy;

    @Nullable
    private String updatedBy;
}

package com.kt.social.domain.user.model;

import com.kt.social.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "user_rela")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserRela extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "follower", nullable = false)
    private User follower; // người theo dõi

    @ManyToOne
    @JoinColumn(name = "following", nullable = false)
    private User following; // người được theo dõi
}

package com.kt.social.domain.post.model;

import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.post.enums.AccessScope;
import com.kt.social.domain.user.model.User;
import com.kt.social.common.entity.BaseEntity;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
//@SQLRestriction("deleted_at IS NULL")
public class Post extends BaseEntity {

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, String>> media;

    private AccessScope accessModifier;

    private int reactCount;

    private int commentCount;

    private int shareCount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_post_id")
    private Post sharedPost;

    /**
     * Nếu sharedPostVisible = false thì UI hiển thị "Bài gốc không còn khả dụng"
     */
    @Builder.Default
    private Boolean sharedPostVisible = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    /**
     * Optimistic locking to help concurrent updates (optional).
     */
    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String violationDetails;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Đánh dấu là bị hệ thống xóa tự động hay admin xóa
    @Column(name = "is_system_ban")
    private boolean isSystemBan;
}
package com.kt.social.domain.post.model;

import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.moderation.enums.ModerationStatus;
import com.kt.social.domain.post.enums.AccessScope;
import com.kt.social.domain.user.model.User;
import com.kt.social.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post extends BaseEntity {

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    private String mediaUrl;

    private AccessScope accessModifier;

    private int reactCount;

    private int commentCount;

    private int shareCount;

    private ModerationStatus moderationStatus;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_post_id")
    private Post sharedPost;

    /**
     * Nếu sharedPostVisible = false thì UI hiển thị "Bài gốc không còn khả dụng"
     */
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
}
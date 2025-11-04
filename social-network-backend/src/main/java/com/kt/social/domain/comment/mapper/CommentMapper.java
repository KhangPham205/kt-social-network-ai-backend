package com.kt.social.domain.comment.mapper;

import com.kt.social.domain.comment.dto.CommentResponse;
import com.kt.social.domain.comment.model.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CommentMapper {

    @Mapping(source = "author.id", target = "authorId")
    @Mapping(source = "author.displayName", target = "authorName")
    @Mapping(source = "author.avatarUrl", target = "authorAvatar")
    @Mapping(source = "post.id", target = "postId")
    @Mapping(source = "parent.id", target = "parentId")
    CommentResponse toDto(Comment comment);
}
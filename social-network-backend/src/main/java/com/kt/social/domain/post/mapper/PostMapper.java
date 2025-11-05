package com.kt.social.domain.post.mapper;

import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.model.Post;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {

    @Mapping(source = "author.id", target = "authorId")
    @Mapping(source = "author.displayName", target = "authorName")
    @Mapping(source = "author.avatarUrl", target = "authorAvatar")
    @Mapping(source = "sharedPost", target = "sharedPost", qualifiedByName = "sharedPostToDto")
    @Mapping(target = "reactSummary", ignore = true)
    @Mapping(target = "shareCount", ignore = true) // shareCount sẽ set bên service
    PostResponse toDto(Post post);

    /**
     * Ánh xạ bài gốc nếu còn khả dụng.
     * Dùng MapStruct recursion-safe để tránh vòng lặp vô hạn
     * (ví dụ: post A share post B, post B share lại post A).
     */
    @Named("sharedPostToDto")
    @Mapping(source = "author.id", target = "authorId")
    @Mapping(source = "author.displayName", target = "authorName")
    @Mapping(source = "author.avatarUrl", target = "authorAvatar")
    @Mapping(target = "sharedPost", ignore = true) // Ngăn vòng lặp vô hạn
    @Mapping(target = "reactSummary", ignore = true)
    @Mapping(target = "shareCount", ignore = true) // shareCount sẽ set ở service
    PostResponse mapSharedPost(Post sharedPost);
}
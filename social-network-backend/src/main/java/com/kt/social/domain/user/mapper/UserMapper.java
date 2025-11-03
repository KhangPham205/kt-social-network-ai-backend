package com.kt.social.domain.user.mapper;

import com.kt.social.domain.user.dto.UserProfileDto;
import com.kt.social.domain.user.model.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "userInfo.bio", target = "bio")
    @Mapping(source = "userInfo.favorites", target = "favorites")
    @Mapping(source = "userInfo.dateOfBirth", target = "dateOfBirth")
    UserProfileDto toDto(User user);
}
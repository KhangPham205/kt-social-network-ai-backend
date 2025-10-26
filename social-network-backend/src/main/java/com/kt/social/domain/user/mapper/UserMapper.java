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


//@Mapper(componentModel = "spring")
//public interface UserMapper {
//    UserProfileDto toDto(User user);
//
//    @AfterMapping
//    default void afterMapping(User user, @MappingTarget UserProfileDto dto) {
//        if (user.getUserInfo() != null) {
//            dto.setBio(user.getUserInfo().getBio());
//            dto.setFavorites(user.getUserInfo().getFavorites());
//            if (user.getUserInfo().getDateOfBirth() != null) {
//                dto.setDateOfBirth(user.getUserInfo().getDateOfBirth());
//            }
//        } else {
//            System.out.println("⚠️ userInfo is NULL for userId = " + user.getId());
//        }
//    }
//}
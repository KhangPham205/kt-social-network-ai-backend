package com.kt.social.domain.user.mapper;

import com.kt.social.auth.model.Role;
import com.kt.social.domain.admin.dto.AdminUserViewDto;
import com.kt.social.domain.user.dto.UserProfileDto;
import com.kt.social.domain.user.model.User;
import org.mapstruct.*;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "userInfo.bio", target = "bio")
    @Mapping(source = "userInfo.favorites", target = "favorites")
    @Mapping(source = "userInfo.dateOfBirth", target = "dateOfBirth")
    UserProfileDto toDto(User user);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "credential.id", target = "credentialId")
    @Mapping(source = "credential.username", target = "username")
    @Mapping(source = "credential.email", target = "email")
    @Mapping(source = "credential.status", target = "status")
    @Mapping(source = "credential.roles", target = "roles", qualifiedByName = "rolesToRoleNames") // Helper
    @Mapping(source = "userInfo.bio", target = "bio")
    @Mapping(source = "userInfo.dateOfBirth", target = "dateOfBirth")
    @Mapping(source = "userInfo.favorites", target = "favorites")
    AdminUserViewDto toAdminViewDto(User user);

    /**
     * Convert Set<Role> sang Set<String>
     */
    @Named("rolesToRoleNames")
    default Set<String> rolesToRoleNames(Set<Role> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }
}
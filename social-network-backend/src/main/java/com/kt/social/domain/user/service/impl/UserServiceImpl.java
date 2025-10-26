package com.kt.social.domain.user.service.impl;

import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.mapper.UserMapper;
import com.kt.social.domain.user.model.*;
import com.kt.social.domain.user.repository.*;
import com.kt.social.domain.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserRelaRepository userRelaRepository;
    private final UserMapper userMapper;

    @Override
    public UserProfileDto getProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserProfileDto dto = userMapper.toDto(user);
        System.out.println("➡️ Mapped dto: " + dto);
        return dto;
    }

    @Override
    public UserProfileDto updateProfile(Long id, UpdateUserProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Chỉ cập nhật nếu displayName không null hoặc không rỗng
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName());
        }

        // Chỉ cập nhật nếu avatarUrl không null hoặc không rỗng
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        UserInfo info = user.getUserInfo();
        if (info == null) {
            info = new UserInfo();
            info.setUser(user);
            user.setUserInfo(info);
        }

        // Chỉ cập nhật nếu bio không null hoặc không rỗng
        if (request.getBio() != null && !request.getBio().isBlank()) {
            info.setBio(request.getBio());
        }

        // Chỉ cập nhật nếu favorites không null hoặc không rỗng
        if (request.getFavorites() != null && !request.getFavorites().isBlank()) {
            info.setFavorites(request.getFavorites());
        }

        // Chỉ cập nhật nếu dateOfBirth không null
        if (request.getDateOfBirth() != null) {
            info.setDateOfBirth(request.getDateOfBirth()
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
        }

        userRepository.save(user);

        UserProfileDto dto = userMapper.toDto(user);
        System.out.println("➡️ Mapped dto: " + dto);
        return dto;
    }

    @Override
    public FollowResponse followUser(Long userId, Long targetId) {
        if (userId.equals(targetId))
            throw new RuntimeException("You cannot follow yourself");

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User following = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        boolean exists = userRelaRepository.existsByFollowerAndFollowing(follower, following);
        if (exists) {
            throw new RuntimeException("Already following");
        }

        UserRela rela = UserRela.builder()
                .follower(follower)
                .following(following)
                .build();

        userRelaRepository.save(rela);
        return new FollowResponse("Followed successfully", true);
    }

    @Override
    @Transactional
    public FollowResponse unfollowUser(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new RuntimeException("You cannot unfollow yourself");
        }

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User following = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        boolean exists = userRelaRepository.existsByFollowerAndFollowing(follower, following);
        if (!exists) {
            throw new RuntimeException("You are not following this user");
        }

        userRelaRepository.deleteByFollowerAndFollowing(follower, following);
        return new FollowResponse("Unfollowed successfully", false);
    }

    @Override
    public List<UserProfileDto> getFollowers(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userRelaRepository.findByFollowing(user)
                .stream()
                .map(UserRela::getFollower)
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserProfileDto> getFollowing(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userRelaRepository.findByFollower(user)
                .stream()
                .map(UserRela::getFollowing)
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }
}
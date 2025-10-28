package com.kt.social.domain.user.service.impl;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final UserRelaRepository userRelaRepository;
    private final UserMapper userMapper;

    @Override
    public User getCurrentUser() {
        String username = SecurityUtils.getCurrentUsername()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        return userRepository.findByCredentialUsername(username);
    }

    @Override
    public UserProfileDto getProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userMapper.toDto(user);
    }

    @Override
    public UserProfileDto updateProfile(UpdateUserProfileRequest request) {
        var credential = SecurityUtils.getCurrentUserCredential(userCredentialRepository)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        User user = userRepository.findById(credential.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        updateUserFields(user, request);
        userRepository.save(user);

        return userMapper.toDto(user);
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

    @Override
    public UserProfileDto getProfileByUsername(String username) {
        UserCredential cred = userCredentialRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        User user = userRepository.findByCredential(cred);
        return userMapper.toDto(user);
    }


    // ---------------------- Helper Method ---------------------------------
    private void updateUserFields(User user, UpdateUserProfileRequest request) {
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        UserInfo info = user.getUserInfo();
        if (info == null) {
            info = new UserInfo();
            info.setUser(user);
            user.setUserInfo(info);
        }

        if (request.getBio() != null && !request.getBio().isBlank()) info.setBio(request.getBio());
        if (request.getFavorites() != null && !request.getFavorites().isBlank()) info.setFavorites(request.getFavorites());
        if (request.getDateOfBirth() != null)
            info.setDateOfBirth(request.getDateOfBirth().atZone(ZoneId.systemDefault()).toInstant());
    }
}
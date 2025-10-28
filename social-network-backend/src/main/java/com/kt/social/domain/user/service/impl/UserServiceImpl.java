package com.kt.social.domain.user.service.impl;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.mapper.UserMapper;
import com.kt.social.domain.user.model.*;
import com.kt.social.domain.user.repository.*;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.storage.service.StorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final UserRelaRepository userRelaRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;

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
    @Transactional
    public UserProfileDto updateProfile(UpdateUserProfileRequest request) {
        var credential = SecurityUtils.getCurrentUserCredential(userCredentialRepository)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        User user = userRepository.findById(credential.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // cập nhật thông tin text
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getBio() != null && user.getUserInfo() != null)
            user.getUserInfo().setBio(request.getBio());
        if (request.getFavorites() != null && user.getUserInfo() != null)
            user.getUserInfo().setFavorites(request.getFavorites());
        if (request.getDateOfBirth() != null && user.getUserInfo() != null)
            user.getUserInfo().setDateOfBirth(request.getDateOfBirth());

        // nếu có file avatar
        if (request.getAvatarFile() != null && !request.getAvatarFile().isEmpty()) {
            String avatarUrl = storageService.saveFile(request.getAvatarFile(), "avatars");

            // xóa avatar cũ nếu có
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
                storageService.deleteFile(user.getAvatarUrl());
            }

            user.setAvatarUrl(avatarUrl);
        }

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

    @Transactional
    @Override
    public UserProfileDto updateAvatar(MultipartFile avatarFile) {
        var credential = SecurityUtils.getCurrentUserCredential(userCredentialRepository)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        User user = userRepository.findById(credential.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Xóa ảnh cũ (nếu có)
        if (user.getAvatarUrl() != null) {
            storageService.deleteFile(user.getAvatarUrl());
        }

        // Lưu ảnh mới
        String avatarUrl = storageService.saveAvatar(avatarFile);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return userMapper.toDto(user);
    }
}
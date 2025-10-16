package com.kt.social.domain.user.service;

import com.kt.social.domain.user.model.User;

public interface UserService {
    User getCurrentUser();
    User updateProfile(User user);
}

package com.kt.social.domain.user.event;

import com.kt.social.domain.user.model.User;

public record UserCreatedEvent(User user) {
}

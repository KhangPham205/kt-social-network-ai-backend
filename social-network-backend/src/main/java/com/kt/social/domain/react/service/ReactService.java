package com.kt.social.domain.react.service;

import com.kt.social.domain.react.dto.ReactRequest;
import com.kt.social.domain.react.dto.ReactResponse;
import com.kt.social.domain.react.enums.TargetType;

public interface ReactService {
    ReactResponse toggleReact(Long userId, ReactRequest req);
    long countReacts(Long targetId, TargetType targetType);
}
package com.kt.social.domain.react.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.react.dto.ReactRequest;
import com.kt.social.domain.react.dto.ReactResponse;
import com.kt.social.domain.react.dto.ReactUserDto;
import com.kt.social.domain.react.enums.TargetType;
import org.springframework.data.domain.Pageable;

public interface ReactService {
    ReactResponse toggleReact(Long userId, ReactRequest req);
    long countReacts(Long targetId, TargetType targetType);
    PageVO<ReactUserDto> getReactUsers(Long targetId, TargetType targetType, Pageable pageable);
}
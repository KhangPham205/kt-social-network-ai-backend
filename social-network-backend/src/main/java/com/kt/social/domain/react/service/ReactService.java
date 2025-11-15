package com.kt.social.domain.react.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.react.dto.ReactRequest;
import com.kt.social.domain.react.dto.ReactResponse;
import com.kt.social.domain.react.dto.ReactSummaryDto;
import com.kt.social.domain.react.dto.ReactUserDto;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.user.model.User;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface ReactService {
    ReactResponse toggleReact(User user, ReactRequest req);
    long countReacts(Long targetId, TargetType targetType);
    PageVO<ReactUserDto> getReactUsers(Long targetId, TargetType targetType, Pageable pageable);
    ReactSummaryDto getReactSummary(Long targetId, TargetType targetType, Long userId);
    Map<Long, ReactSummaryDto> getReactSummaries(List<Long> targetIds, Long viewerId, TargetType targetType);
}
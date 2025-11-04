package com.kt.social.domain.react.controller;

import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.react.dto.ReactRequest;
import com.kt.social.domain.react.dto.ReactResponse;
import com.kt.social.domain.react.dto.ReactUserDto;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reacts")
@RequiredArgsConstructor
public class ReactController {

    private final ReactService reactService;
    private final UserService userService;

    @PostMapping("/toggle")
    public ResponseEntity<ReactResponse> toggle(@RequestBody ReactRequest req) {
        Long userId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(reactService.toggleReact(userId, req));
    }

    @GetMapping("/{targetType}/{targetId}/users")
    public ResponseEntity<PageVO<ReactUserDto>> getReactUsers(
            @PathVariable TargetType targetType,
            @PathVariable Long targetId,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(reactService.getReactUsers(targetId, targetType, pageable));
    }
}
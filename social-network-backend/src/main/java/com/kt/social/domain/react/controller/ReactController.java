package com.kt.social.domain.react.controller;

import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.domain.react.dto.ReactRequest;
import com.kt.social.domain.react.dto.ReactResponse;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
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
}
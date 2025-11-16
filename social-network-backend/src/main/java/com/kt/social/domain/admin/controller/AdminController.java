package com.kt.social.domain.admin.controller;

import com.kt.social.auth.dto.CreateStaffRequest;
import com.kt.social.auth.dto.RegisterResponse;
import com.kt.social.auth.service.AuthService;
import com.kt.social.domain.admin.dto.AdminUserViewDto;
import com.kt.social.domain.audit.dto.ActivityLogDto;
import com.kt.social.domain.audit.service.ActivityLogService; // (Cần thêm hàm get)
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/activity")
@RequiredArgsConstructor
public class AdminController {

    private final ActivityLogService activityLogService;
    private final AuthService authService;
    private final UserService userService;

    // (Bạn cần thêm hàm 'getLogsForUser' vào Service)
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('ADMIN:READ')") // Bảo vệ
    public ResponseEntity<PageVO<ActivityLogDto>> getLogsForUser(
            @PathVariable Long userId,
            @RequestParam(value = "filter", required = false) String filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(activityLogService.getLogsForUser(userId, filter, pageable));
    }

    @PostMapping("/create-staff")
    @PreAuthorize("hasAuthority('USER:CREATE')")
    public ResponseEntity<RegisterResponse> createStaff(@RequestBody CreateStaffRequest request) {
        return ResponseEntity.ok(authService.createStaffAccount(request));
    }

    /**
     * C(R)UD: Read (Get All)
     * (Yêu cầu quyền USER:READ_ALL)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('USER:READ_ALL')")
    public ResponseEntity<PageVO<AdminUserViewDto>> getAllUsers(
            @RequestParam(value = "filter", required = false) String filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(userService.getAllUsers(filter, pageable));
    }

    /**
     * C(R)UD: Read (Get One)
     * (Yêu cầu quyền USER:READ_ALL)
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER:READ_ALL')")
    public ResponseEntity<AdminUserViewDto> getUserByIdAsAdmin(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserByIdAsAdmin(userId));
    }

    /**
     * CR(U)D: Update
     * (Yêu cầu quyền USER:UPDATE_ANY)
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER:UPDATE_ANY')")
    public ResponseEntity<AdminUserViewDto> updateUserAsAdmin(
            @PathVariable Long userId,
            @RequestBody AdminUpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.updateUserAsAdmin(userId, request));
    }

    /**
     * CRU(D): Delete (Thực tế là Ban/Cấm)
     * (Yêu cầu quyền USER:DELETE_ANY)
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER:DELETE_ANY')")
    public ResponseEntity<Void> deleteUserAsAdmin(@PathVariable Long userId) {
        userService.deleteUserAsAdmin(userId);
        return ResponseEntity.noContent().build();
    }
}

package com.kt.social.config;

// ... (Các import của bạn giữ nguyên)
import com.kt.social.auth.model.Permission;
import com.kt.social.auth.model.Role;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.PermissionRepository;
import com.kt.social.auth.repository.RoleRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.model.UserInfo;
import com.kt.social.domain.user.repository.UserInfoRepository;
import com.kt.social.domain.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @PostConstruct
    @Transactional
    public void init() {
        System.out.println("Initializing default roles, permissions, and admin account...");

        Role userRole = findOrCreateRole("USER", "Standard user role");
        Role adminRole = findOrCreateRole("ADMIN", "Administrator role");
        Role moderatorRole = findOrCreateRole("MODERATOR", "Moderation role");

        Permission createPost = findOrCreatePermission("POST", "CREATE", "Create a new post");
        Permission updatePost = findOrCreatePermission("POST", "UPDATE", "Update own post");
        Permission deletePost = findOrCreatePermission("POST", "DELETE", "Delete own post");
        Permission createComment = findOrCreatePermission("COMMENT", "CREATE", "Create a comment");
        Permission updateComment = findOrCreatePermission("COMMENT", "UPDATE", "Update own comment");
        Permission deleteComment = findOrCreatePermission("COMMENT", "DELETE", "Delete own comment");

        Permission deleteAnyPost = findOrCreatePermission("POST", "DELETE_ANY", "Delete any post");
        Permission deleteAnyComment = findOrCreatePermission("COMMENT", "DELETE_ANY", "Delete any comment");
        Permission readAdminDashboard = findOrCreatePermission("ADMIN", "READ", "Access Admin Dashboard");
        Permission createStaff = findOrCreatePermission("USER", "CREATE", "Create new Staff Account (Admin/Mod)");
        Permission deleteUser = findOrCreatePermission("USER", "DELETE", "Delete any User Account");

        assignPermissions(userRole, Set.of(
                createPost, updatePost, deletePost,
                createComment, updateComment, deleteComment
        ));

        assignPermissions(moderatorRole, Set.of(
                deleteAnyPost, deleteAnyComment
        ));

        assignPermissions(adminRole, Set.of(
                createPost, updatePost, deletePost,
                createComment, updateComment, deleteComment,
                deleteAnyPost, deleteAnyComment,
                readAdminDashboard, createStaff, deleteUser
        ));

        if (!userCredentialRepository.existsByUsername(adminUsername)) {

            UserCredential adminCredential = UserCredential.builder()
                    .username(adminUsername)
                    .email("admin@social.local")
                    .password(passwordEncoder.encode(adminPassword))
                    .roles(Set.of(adminRole))
                    .status(AccountStatus.ACTIVE)
                    .build();

            User adminUser = User.builder()
                    .displayName("Administrator")
                    .isActive(true)
                    .build();

            UserInfo adminInfo = UserInfo.builder()
                    .bio("Tài khoản quản trị viên")
                    .build();

            adminCredential.setUser(adminUser);
            adminUser.setCredential(adminCredential);
            adminUser.setUserInfo(adminInfo);
            adminInfo.setUser(adminUser);

            userRepository.save(adminUser);
            System.out.println("✅ Default admin account (với profile) created: " + adminUsername);
        } else {
            System.out.println("ℹ️ Admin account already exists, skipping creation.");
        }

        System.out.println("✅ Initialization completed successfully.");
    }

    // -------------------- Helper methods --------------------

    private Role findOrCreateRole(String name, String description) {
        return roleRepository.findByName(name.toUpperCase())
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .name(name.toUpperCase())
                            .description(description)
                            .build();
                    roleRepository.save(newRole);
                    System.out.println("🟢 Created role: " + name.toUpperCase());
                    return newRole;
                });
    }

    private Permission findOrCreatePermission(String resource, String action, String description) {
        String name = resource.toUpperCase() + ":" + action.toUpperCase();
        return permissionRepository.findByName(name)
                .orElseGet(() -> {
                    Permission newPerm = Permission.builder()
                            .resource(resource.toUpperCase())
                            .action(action.toUpperCase())
                            .name(name) // Tên chuẩn hóa
                            .description(description)
                            .build();
                    permissionRepository.save(newPerm);
                    System.out.println("🟢 Created permission: " + name);
                    return newPerm;
                });
    }

    private void assignPermissions(Role role, Set<Permission> permissions) {
        boolean updated = false;
        if (role.getPermissions() == null) {
            role.setPermissions(new HashSet<>());
        }
        for (Permission permission : permissions) {
            if (!role.getPermissions().contains(permission)) {
                role.getPermissions().add(permission);
                updated = true;
            }
        }
        if (updated) {
            roleRepository.save(role);
            System.out.println("Updated permissions for role: " + role.getName());
        }
    }
}
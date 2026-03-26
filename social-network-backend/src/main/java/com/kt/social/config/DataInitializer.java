package com.kt.social.config;

import com.kt.social.auth.model.Permission;
import com.kt.social.auth.model.Role;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.PermissionRepository;
import com.kt.social.auth.repository.RoleRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.domain.react.model.ReactType;
import com.kt.social.domain.react.repository.ReactTypeRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.model.UserInfo;
import com.kt.social.domain.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final ReactTypeRepository reactTypeRepository;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @PostConstruct
    @Transactional
    public void init() {
        System.out.println("Initializing system data (Roles, Permissions, Admin)...");

        // 1. ROLES
        Role userRole = findOrCreateRole("USER", "Standard user role");
        Role adminRole = findOrCreateRole("ADMIN", "Administrator role");
        Role moderatorRole = findOrCreateRole("MODERATOR", "Content Moderator role");

        // 2. PERMISSIONS

        // --- Post & Comment (User Basic) ---
        Permission createPost = findOrCreatePermission("POST", "CREATE", "Create a new post");
        Permission updatePost = findOrCreatePermission("POST", "UPDATE", "Update own post");
        Permission deletePost = findOrCreatePermission("POST", "DELETE", "Delete own post");
        Permission createComment = findOrCreatePermission("COMMENT", "CREATE", "Create a comment");
        Permission updateComment = findOrCreatePermission("COMMENT", "UPDATE", "Update own comment");
        Permission deleteComment = findOrCreatePermission("COMMENT", "DELETE", "Delete own comment");

        // --- User Actions (Report & Complaint) ---
        Permission createReport = findOrCreatePermission("REPORT", "CREATE", "Create a new report");
        Permission createComplaint = findOrCreatePermission("COMPLAINT", "CREATE", "Create a complaint for banned content");

        // --- Moderation Actions (Dành cho Mod/Admin) ---

        // >> Xử lý Post/Comment
        Permission deleteAnyPost = findOrCreatePermission("POST", "DELETE_ANY", "Delete any post (Moderation)");
        Permission deleteAnyComment = findOrCreatePermission("COMMENT", "DELETE_ANY", "Delete any comment (Moderation)");

        // >> Xử lý Report
        Permission viewAllReports = findOrCreatePermission("REPORT", "VIEW_ALL", "View all reports");
        Permission processReport = findOrCreatePermission("REPORT", "PROCESS", "Approve or Reject reports");

        // >> Xử lý Complaint (Khiếu nại) - Đã sửa cho khớp Controller
        Permission viewAllComplaints = findOrCreatePermission("COMPLAINT", "VIEW_ALL", "View all complaints"); // Mới thêm
        Permission processComplaint = findOrCreatePermission("COMPLAINT", "PROCESS", "Process/Resolve user complaints"); // Đổi tên từ RESOLVE -> PROCESS

        // >> Quản lý User (Khóa/Mở khóa/Xem tin riêng tư)
        Permission blockUser = findOrCreatePermission("USER", "BLOCK", "Block/Unblock user account");
        Permission readSensitiveUser = findOrCreatePermission("USER", "READ_SENSITIVE", "View sensitive user info (email, violations)");
        Permission readAnyMessage = findOrCreatePermission("MESSAGE", "READ_ANY", "Read any message content for moderation");

        // >> Dashboard
        Permission moderationAccess = findOrCreatePermission("MODERATION", "ACCESS", "Access Moderation Dashboard");

        // --- Admin Only ---
        Permission readAdminDashboard = findOrCreatePermission("ADMIN", "READ", "Access Admin Dashboard");
        Permission createStaff = findOrCreatePermission("USER", "CREATE", "Create new Staff Account");
        Permission readAllUsers = findOrCreatePermission("USER", "READ_ALL", "Read list of all users");
        Permission updateAnyUser = findOrCreatePermission("USER", "UPDATE_ANY", "Update any user profile");
        Permission deleteAnyUser = findOrCreatePermission("USER", "DELETE_ANY", "Hard delete user data");


        // 3. ASSIGN PERMISSIONS

        // -> USER: Đăng bài, cmt, report, khiếu nại
        assignPermissions(userRole, Set.of(
                createPost, updatePost, deletePost,
                createComment, updateComment, deleteComment,
                createReport, createComplaint
        ));

        // -> MODERATOR: Xóa bài, Xử lý report/khiếu nại, Khóa user, Xem info nhạy cảm
        assignPermissions(moderatorRole, Set.of(
                deleteAnyPost, deleteAnyComment,
                viewAllReports, processReport,
                viewAllComplaints, processComplaint, // Đã cập nhật
                blockUser, readSensitiveUser,
                readAnyMessage, moderationAccess
        ));

        // -> ADMIN: Full quyền Mod + Quản trị hệ thống
        Set<Permission> adminPermissions = new HashSet<>();
        // Admin làm được mọi thứ User làm
        adminPermissions.addAll(userRole.getPermissions());
        // Admin làm được mọi thứ Mod làm
        adminPermissions.addAll(moderatorRole.getPermissions());
        // Quyền riêng của Admin
        adminPermissions.addAll(Set.of(
                readAdminDashboard, createStaff,
                readAllUsers, updateAnyUser, deleteAnyUser
        ));

        assignPermissions(adminRole, adminPermissions);

        // 4. INIT REACT TYPES (MỚI THÊM)
        initReactTypes();

        // 5. CREATE DEFAULT ADMIN
        createDefaultAdmin(adminRole);

        // 6. SEED 1000 TEST USERS
//        createTestUsers(userRole);

        System.out.println("✅ Initialization completed successfully.");
    }

    // -------------------- Helper methods (Giữ nguyên) --------------------

    private void createDefaultAdmin(Role adminRole) {
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
                    .avatarUrl("https://ui-avatars.com/api/?name=Admin&background=0D8ABC&color=fff")
                    .build();

            UserInfo adminInfo = UserInfo.builder()
                    .bio("System Administrator")
                    .build();

            adminCredential.setUser(adminUser);
            adminUser.setCredential(adminCredential);
            adminUser.setUserInfo(adminInfo);
            adminInfo.setUser(adminUser);

            userRepository.save(adminUser);
            System.out.println("✅ Default admin created: " + adminUsername);
        } else {
            System.out.println("ℹ️ Admin account already exists.");
        }
    }

    private Role findOrCreateRole(String name, String description) {
        return roleRepository.findByName(name.toUpperCase())
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .name(name.toUpperCase())
                            .description(description)
                            .build();
                    return roleRepository.save(newRole);
                });
    }

    private Permission findOrCreatePermission(String resource, String action, String description) {
        String name = resource.toUpperCase() + ":" + action.toUpperCase();
        return permissionRepository.findByName(name)
                .orElseGet(() -> {
                    Permission newPerm = Permission.builder()
                            .resource(resource.toUpperCase())
                            .action(action.toUpperCase())
                            .name(name)
                            .description(description)
                            .build();
                    return permissionRepository.save(newPerm);
                });
    }

    private void assignPermissions(Role role, Set<Permission> permissions) {
        if (role.getPermissions() == null) {
            role.setPermissions(new HashSet<>());
        }

        // Thêm quyền mới nếu chưa có
        boolean changed = false;
        for (Permission p : permissions) {
            if (!role.getPermissions().contains(p)) {
                role.getPermissions().add(p);
                changed = true;
            }
        }

        if (changed) {
            roleRepository.save(role);
            System.out.println("🔄 Updated permissions for role: " + role.getName());
        }
    }

    private void initReactTypes() {
        findOrCreateReactType("LIKE", "👍");
        findOrCreateReactType("LOVE", "❤️");
        findOrCreateReactType("HAHA", "😂");
        findOrCreateReactType("WOW", "😮");
        findOrCreateReactType("SAD", "😢");
        findOrCreateReactType("ANGRY", "😡");
        System.out.println("✅ React types initialized.");
    }

    private void findOrCreateReactType(String name, String charSymbol) {
        if (!reactTypeRepository.existsByName(name)) {
            ReactType type = ReactType.builder()
                    .name(name)
                    .charSymbol(charSymbol)
                    .iconUrl(null)
                    .build();
            reactTypeRepository.save(type);
        }
    }

    /**
     * Khởi tạo 1000 user test cho việc đo lường hiệu năng
     * Sử dụng batch processing để tối ưu hiệu năng
     */
    @Transactional
    protected void createTestUsers(Role userRole) {
        // Kiểm tra nếu đã có user test
        long existingUserCount = userRepository.count();
        if (existingUserCount >= 1001) { // Có admin (1) + 1000 test users
            System.out.println("ℹ️ Test users already exist. Skipping creation.");
            return;
        }

        System.out.println("🚀 Starting to create 1000 test users...");
        long startTime = System.currentTimeMillis();

        int totalUsers = 1000;
        int batchSize = 50; // Xử lý 50 user mỗi batch để tránh out of memory
        List<User> userBatch = new ArrayList<>();

        String[] firstNames = {"Phạm", "Nguyễn", "Trần", "Hoàng", "Vũ", "Đặng", "Bùi", "Đinh", "Dương", "Lý"};
        String[] lastNames = {"Tuấn", "Minh", "Hùng", "Thủy", "Linh", "Khang", "Anh", "Bình", "Công", "Đạt"};
        String[] bioTemplates = {
                "I love coding!",
                "Developer passionate about tech",
                "Learning Spring Boot",
                "Java enthusiast",
                "Building amazing applications",
                "Coffee and code lover",
                "Tech enthusiast",
                "Passionate coder",
                "Always learning",
                "Software engineer"
        };
        String[] favorites = {"Java, Spring", "React, Node", "Python, Django", "JavaScript", "Kotlin", "Go", "Rust", "C#"};

        Random random = new Random(42); // Fixed seed để có kết quả reproducible

        try {
            for (int i = 1; i <= totalUsers; i++) {
                // Tạo UserCredential
                String username = "testuser" + i;
                String email = "testuser" + i + "@test.local";

                UserCredential credential = UserCredential.builder()
                        .username(username)
                        .email(email)
                        .password(passwordEncoder.encode("password123")) // Mật khẩu chung cho test
                        .status(AccountStatus.ACTIVE)
                        .roles(Set.of(userRole))
                        .build();

                // Tạo User
                String firstName = firstNames[random.nextInt(firstNames.length)];
                String lastName = lastNames[random.nextInt(lastNames.length)];
                String displayName = firstName + " " + lastName + " " + i;

                User user = User.builder()
                        .displayName(displayName)
                        .avatarUrl("https://ui-avatars.com/api/?name=" + displayName.replace(" ", "+") + "&background=random")
                        .credential(credential)
                        .build();

                // Tạo UserInfo
                UserInfo userInfo = UserInfo.builder()
                        .bio(bioTemplates[random.nextInt(bioTemplates.length)])
                        .favorites(favorites[random.nextInt(favorites.length)])
                        .dateOfBirth(generateRandomDate(random))
                        .user(user)
                        .build();

                user.setUserInfo(userInfo);
                credential.setUser(user);

                userBatch.add(user);

                // Xử lý batch
                if (userBatch.size() == batchSize || i == totalUsers) {
                    userRepository.saveAll(userBatch);
                    System.out.println("✅ Created " + i + " / " + totalUsers + " test users");
                    userBatch.clear();
                }
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.println("✅ Successfully created 1000 test users in " + duration + "ms");

        } catch (Exception e) {
            System.err.println("❌ Error creating test users: " + e.getMessage());
            log.error("Error creating test users", e);
        }
    }

    /**
     * Tạo một ngày sinh ngẫu nhiên giữa 18 và 70 tuổi
     */
    private Instant generateRandomDate(Random random) {
        // Sinh nhật giữa 18 và 70 tuổi
        long currentTime = Instant.now().toEpochMilli();
        long minAge = 18 * 365L * 24 * 60 * 60 * 1000; // 18 năm trước
        long maxAge = 70 * 365L * 24 * 60 * 60 * 1000; // 70 năm trước

        long randomTime = currentTime - minAge - random.nextLong() % (maxAge - minAge);
        return Instant.ofEpochMilli(randomTime);
    }
}
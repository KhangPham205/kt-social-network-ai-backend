package com.kt.social.infra.neo4j.graph.service;

import com.kt.social.infra.neo4j.graph.node.UserNode;
import com.kt.social.infra.neo4j.graph.repository.UserGraphRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphSyncService {

    private final UserGraphRepository graphRepository;
    private final UserRepository userRepository; // Postgres Repo

    // 1. Hàm đồng bộ User (Node)
    @Transactional(transactionManager = "neo4jTransactionManager")
    public void syncUser(User user) {
        saveUserNode(user);
    }

    // 2. Hàm tạo kết bạn (Relationship) -> Đã viết lại hoàn toàn
    @Transactional(transactionManager = "neo4jTransactionManager")
    public void createFriendship(Long userId1, Long userId2) {
        // Bước 1: Chắc chắn rằng 2 Node này đã có trong Neo4j
        // (Nếu chưa có thì tạo mới ngay lập tức)
        ensureUserNodeExists(userId1);
        ensureUserNodeExists(userId2);

        // Bước 2: Nối dây bằng Native Query (An toàn tuyệt đối)
        graphRepository.createFriendshipRelation(userId1, userId2);
        // Vì Neo4j là đồ thị có hướng, để thuật toán gợi ý hoạt động tốt nhất,
        // ta có thể tạo chiều ngược lại hoặc query không hướng.
        // Câu lệnh dưới đây tạo chiều ngược lại cho chắc chắn:
        graphRepository.createFriendshipRelation(userId2, userId1);

        log.info("✅ Neo4j: Đã kết nối {} <-> {}", userId1, userId2);
    }

    // 3. Hàm xóa kết bạn
    @Transactional(transactionManager = "neo4jTransactionManager")
    public void removeFriendship(Long userId1, Long userId2) {
        graphRepository.deleteFriendshipRelation(userId1, userId2);
        log.info("❌ Neo4j: Đã xóa kết nối {} <-> {}", userId1, userId2);
    }

    // ---------------- HELPER METHODS ----------------

    private void ensureUserNodeExists(Long userId) {
        // Kiểm tra xem có chưa, nếu chưa thì lấy từ Postgres sang
        if (!graphRepository.existsById(userId)) {
            log.warn("UserNode {} chưa có trong Neo4j. Đang tạo bù...", userId);
            userRepository.findById(userId).ifPresent(this::saveUserNode);
        }
    }

    private void saveUserNode(User user) {
        UserNode node = UserNode.builder()
                .userId(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .build();
        graphRepository.save(node);
    }
}
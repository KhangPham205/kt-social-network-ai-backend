package com.kt.social.domain.post.service;

import com.kt.social.infra.ai.AiServiceClient;
import com.kt.social.infra.milvus.MilvusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostSyncService {

    private final AiServiceClient aiServiceClient;
    private final MilvusService milvusService;

    /**
     * Chạy bất đồng bộ ở luồng riêng
     */
    @Async
    public void syncPostToMilvus(Long postId, Long authorId, String content) {
        try {
            log.info("🤖 AI Sync: Đang tạo vector cho Post ID {}", postId);

            // 1. Gọi Python lấy Vector
            List<Float> vector = aiServiceClient.getEmbedding(content);

            // 2. Lưu vào Milvus
            if (!vector.isEmpty()) {
                milvusService.savePostVector(postId, authorId, vector);
                log.info("✅ AI Sync: Đã lưu vector thành công cho Post ID {}", postId);
            } else {
                log.warn("⚠️ AI Sync: Vector rỗng cho Post ID {}", postId);
            }
        } catch (Exception e) {
            // Chỉ log lỗi, không làm ảnh hưởng luồng chính
            log.error("❌ AI Sync Failed cho Post ID {}: {}", postId, e.getMessage());
        }
    }
}

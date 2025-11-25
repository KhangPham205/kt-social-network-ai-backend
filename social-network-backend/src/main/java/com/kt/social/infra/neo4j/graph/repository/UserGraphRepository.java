package com.kt.social.infra.neo4j.graph.repository;

import com.kt.social.infra.neo4j.graph.node.UserNode;
import lombok.Data;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RelationshipProperties
public interface UserGraphRepository extends Neo4jRepository<UserNode, Long> {

    /**
     * Gợi ý kết bạn dựa trên bạn chung
     * Logic:
     * (Me)-[:FRIEND]-(Friend)-[:FRIEND]-(Recommendation)
     * Tìm những người (Recommendation) chưa kết bạn với Me
     */
    @Query("MATCH (me:User {userId: $userId}) " +
            "MATCH path = (me)-[:FRIEND*2..3]-(fof:User) " +
            "WHERE NOT (me)-[:FRIEND]-(fof) AND me <> fof " +
            "WITH fof, length(path) as len, nodes(path)[1] as friend " +
            "WITH fof, min(len) as depth, count(DISTINCT friend) as calculatedMutualCount " +
            "RETURN fof.userId AS userId, " +
            "       fof.displayName AS displayName, " +
            "       fof.avatarUrl AS avatarUrl, " +
            "       depth, " +
            "       CASE WHEN depth = 2 THEN calculatedMutualCount ELSE 0 END AS mutualCount " +
            "ORDER BY depth ASC, mutualCount DESC " +
            "LIMIT $limit")
    List<RecommendationResultDto> findRecommendations(Long userId, int limit);

    @Query("MATCH (u1:User {userId: $userId1}), (u2:User {userId: $userId2}) " +
            "MERGE (u1)-[:FRIEND]->(u2)")
    void createFriendshipRelation(Long userId1, Long userId2);

    @Query("MATCH (u1:User {userId: $userId1})-[r:FRIEND]-(u2:User {userId: $userId2}) DELETE r")
    void deleteFriendshipRelation(Long userId1, Long userId2);

    @Data
    class RecommendationResultDto {
        private Long userId;
        private String displayName;
        private String avatarUrl;
        private Long mutualCount; // Lưu ý: COUNT trong Neo4j trả về Long, không phải int
        private Integer depth;
    }
}

package com.kt.social.infra.neo4j.graph.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("User") // Label trong Neo4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNode {

    @Id
    private Long userId; // Map với ID của PostgreSQL

    private String displayName;
    private String avatarUrl;

    // Quan hệ bạn bè (Vô hướng - Undirected trong logic, nhưng Neo4j lưu có hướng)
    @Relationship(type = "FRIEND", direction = Relationship.Direction.OUTGOING)
    private Set<UserNode> friends = new HashSet<>();
}

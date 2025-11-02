package com.kt.social.domain.react.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "react_type")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // ví dụ: LIKE, LOVE, HAHA, SAD

    private String charSymbol; // optional short label

    private String iconUrl;
}
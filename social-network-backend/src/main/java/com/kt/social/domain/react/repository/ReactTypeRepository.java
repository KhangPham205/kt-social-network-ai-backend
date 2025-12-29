package com.kt.social.domain.react.repository;

import com.kt.social.domain.react.model.ReactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReactTypeRepository extends JpaRepository<ReactType, Long> {
    boolean existsByName(String name);
}
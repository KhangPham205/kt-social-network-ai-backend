package com.kt.social.domain.user.repository;

import com.kt.social.domain.user.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

}


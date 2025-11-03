package com.kt.social.domain.user.repository;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    User findByCredential(UserCredential cred);
    User findByCredentialUsername(String username);
}

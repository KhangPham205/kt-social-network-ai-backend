package com.kt.social.auth.service;

import com.kt.social.auth.model.Permission;
import java.util.List;

public interface PermissionService {
    Permission create(Permission permission);
    List<Permission> getAll();
    Permission update(Long id, Permission permission);
    void delete(Long id);
}
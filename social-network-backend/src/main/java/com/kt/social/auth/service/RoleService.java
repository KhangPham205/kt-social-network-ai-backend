package com.kt.social.auth.service;

import com.kt.social.auth.model.Role;

import java.util.List;

public interface RoleService {
    Role assignPermission(Long roleId, Long permissionId);
    Role removePermission(Long roleId, Long permissionId);
    List<Role> getAll();
}

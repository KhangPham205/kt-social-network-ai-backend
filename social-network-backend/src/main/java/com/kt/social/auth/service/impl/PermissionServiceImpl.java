package com.kt.social.auth.service.impl;

import com.kt.social.auth.model.Permission;
import com.kt.social.auth.repository.PermissionRepository;
import com.kt.social.auth.service.PermissionService;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

    @Override
    public Permission create(Permission permission) {
        if (permissionRepository.existsByName(permission.getName())) {
            throw new BadRequestException("Permission already exists: " + permission.getName());
        }
        return permissionRepository.save(permission);
    }

    @Override
    public List<Permission> getAll() {
        return permissionRepository.findAll();
    }

    @Override
    public Permission update(Long id, Permission permission) {
        Permission existingPermission = permissionRepository.findById(permission.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));

        existingPermission.setName(permission.getName());
        existingPermission.setDescription(permission.getDescription());

        return permissionRepository.save(existingPermission);
    }

    @Override
    public void delete(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy quyền (Permission) với ID: " + id);
        }
        permissionRepository.deleteById(id);
    }}

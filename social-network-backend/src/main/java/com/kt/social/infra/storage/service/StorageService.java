package com.kt.social.infra.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.avatar-dir}")
    private String avatarDir;

    @Value("${file.base-url}")
    private String baseUrl;

    /**
     * Lưu file chung trong thư mục con (ví dụ "avatars", "posts", "comments")
     */
    public String saveFile(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or null");
        }

        try {
            Path dirPath = Paths.get(uploadDir, subDir).normalize();
            Files.createDirectories(dirPath);

            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            String filename = UUID.randomUUID() + "_" + originalFilename;
            Path filePath = dirPath.resolve(filename);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return baseUrl + "/" + subDir + "/" + filename;

        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to store file: " + e.getMessage(), e);
        }
    }

    /**
     * Lưu riêng avatar người dùng
     */
    public String saveAvatar(MultipartFile avatarFile) {
        return saveFile(avatarFile, avatarDir);
    }

    /**
     * Xóa file theo URL public
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        try {
            String relativePath = fileUrl.replace(baseUrl + "/", "");
            Path filePath = Paths.get(uploadDir).resolve(relativePath).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.err.println("⚠️ Failed to delete old file: " + e.getMessage());
        }
    }

    /**
     * Làm sạch tên file tránh lỗi path traversal
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
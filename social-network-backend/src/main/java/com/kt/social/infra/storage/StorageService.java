package com.kt.social.infra.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.kt.social.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Thêm Log
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL; // Thêm thư viện URL
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j // Thêm annotation này để log lỗi
public class StorageService {

    private final Cloudinary cloudinary;

    /**
     * Upload file lên Cloudinary
     */
    public String saveFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty or null");
        }

        try {
            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "auto" // Cho phép ảnh hoặc video
                    )
            );

            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            throw new BadRequestException("Upload to Cloudinary failed: " + e.getMessage());
        }
    }

    /**
     * Đọc file từ URL Cloudinary về dạng byte[]
     * Dùng để gửi ảnh qua AI Service kiểm tra
     */
    public byte[] readFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;

        try (InputStream in = new URL(fileUrl).openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.error("❌ Không thể tải file từ URL: {}. Lỗi: {}", fileUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Xóa file trên Cloudinary
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        try {
            // Logic trích xuất Public ID chính xác hơn
            // URL mẫu: https://res.cloudinary.com/demo/image/upload/v123456/folder_name/image_name.jpg

            // 1. Xác định loại file (image/video)
            String ext = fileUrl.substring(fileUrl.lastIndexOf('.') + 1).toLowerCase();
            String resourceType = List.of("mp4", "mov", "avi", "webm").contains(ext) ? "video" : "image";

            // 2. Lấy Public ID (Bao gồm cả folder nếu có)
            // Cắt từ sau chữ "upload/" và bỏ version (v12345...)
            String publicId = extractPublicId(fileUrl);

            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
                log.info("Deleted file on Cloudinary: {}", publicId);
            }

        } catch (IOException e) {
            log.error("Failed to delete from Cloudinary: {}", e.getMessage());
        }
    }

    // Helper: Trích xuất Public ID từ URL Cloudinary
    private String extractPublicId(String url) {
        try {
            int uploadIndex = url.indexOf("/upload/");
            if (uploadIndex == -1) return null;

            // Lấy phần sau /upload/ -> "v12345/folder/abc.jpg"
            String path = url.substring(uploadIndex + 8);

            // Bỏ phần version "v12345/" nếu có
            int slashIndex = path.indexOf("/");
            if (slashIndex != -1 && path.substring(0, slashIndex).startsWith("v")) {
                path = path.substring(slashIndex + 1);
            }

            // Bỏ đuôi mở rộng (.jpg) -> "folder/abc"
            int dotIndex = path.lastIndexOf(".");
            if (dotIndex != -1) {
                path = path.substring(0, dotIndex);
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }
}
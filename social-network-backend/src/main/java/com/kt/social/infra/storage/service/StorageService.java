package com.kt.social.infra.storage.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.kt.social.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final Cloudinary cloudinary;

    /**
     * Upload file lên Cloudinary
     * @param file file gửi từ client
     * @param folder thư mục con trên Cloudinary (vd: "posts", "avatars", ...)
     * @return URL public của file
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
     * Xóa file trên Cloudinary dựa theo URL public
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        try {
            // Extract public_id từ URL: https://res.cloudinary.com/<cloud_name>/image/upload/v123456/posts/abc123.jpg
            String[] parts = fileUrl.split("/");
            String publicIdWithExt = parts[parts.length - 1]; // abc123.jpg
            String publicId = publicIdWithExt.substring(0, publicIdWithExt.lastIndexOf('.'));

            // Xóa theo public_id
            cloudinary.uploader().destroy("posts/" + publicId, ObjectUtils.asMap("resource_type", "auto"));

        } catch (IOException e) {
            System.err.println("Failed to delete from Cloudinary: " + e.getMessage());
        }
    }
}
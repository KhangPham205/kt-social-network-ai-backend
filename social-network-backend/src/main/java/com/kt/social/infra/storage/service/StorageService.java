package com.kt.social.infra.storage.service;

import com.kt.social.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;  // Ví dụ: C:/uploads/

    @Value("${file.base-url}")
    private String baseUrl;    // Ví dụ: http://localhost:8080/files/

    /**
     * Lưu file vào thư mục con cụ thể (ví dụ "posts/media").
     * Trả về đường dẫn URL công khai dạng http://localhost:8080/files/posts/media/xyz.jpg
     */
    public String saveFile(MultipartFile file, String subFolder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty or null");
        }

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "." + extension;
        Path targetFolder = Paths.get(uploadDir, subFolder).toAbsolutePath().normalize();

        try {
            Files.createDirectories(targetFolder);
            Path targetFile = targetFolder.resolve(fileName);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

            return baseUrl + "/" + subFolder + "/" + fileName;
        } catch (IOException e) {
            throw new IOException("Failed to save file: " + fileName, e);
        } catch (Error e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Xóa file dựa trên URL public
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        try {
            String relativePath = fileUrl.replace(baseUrl + "/", "");
            Path filePath = Paths.get(uploadDir).resolve(relativePath).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.err.println("Failed to delete old file: " + e.getMessage());
        }
    }
}
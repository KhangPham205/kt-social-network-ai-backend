package com.kt.social.infra.storage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final List<String> VIDEO_TYPES = List.of("mp4", "webm", "ogg", "mov", "quicktime");
    private static final List<String> IMAGE_TYPES = List.of("jpg", "jpeg", "png", "gif", "webp");

    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<Resource> getFile(
            @PathVariable String folder,
            @PathVariable String filename
    ) throws IOException {

        Path filePath = Paths.get(uploadDir, folder, filename).normalize();
        File file = filePath.toFile();

        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        // Sử dụng FileSystemResource cho tất cả
        FileSystemResource resource = new FileSystemResource(file);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        String extension = getExtension(filename);
        boolean isMedia = VIDEO_TYPES.contains(extension.toLowerCase()) ||
                IMAGE_TYPES.contains(extension.toLowerCase());

        if (isMedia) {
            // Đối với video và ảnh, chỉ cần trả về resource.
            // Spring sẽ TỰ ĐỘNG xử lý 'Range' header (streaming) cho video.
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(resource);
        } else {
            // Đối với các file khác, ép tải xuống (download)
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        return (dotIndex != -1) ? filename.substring(dotIndex + 1).toLowerCase() : "";
    }
}
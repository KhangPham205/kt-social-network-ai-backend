package com.kt.social.infra.storage.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/videos")
public class VideoController {

    @Value("${file.upload-dir}")
    private String baseUploadDir;

    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<ResourceRegion> streamVideo(
            @PathVariable String folder,
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws IOException {

        Path videoPath = Paths.get(baseUploadDir, folder, filename);
        UrlResource video = new UrlResource(videoPath.toUri());

        long contentLength = video.contentLength();
        ResourceRegion region = resourceRegion(video, headers, contentLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaTypeFactory.getMediaType(video)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(region);
    }

    private ResourceRegion resourceRegion(UrlResource video, HttpHeaders headers, long contentLength) throws IOException {
        long rangeStart = 0;
        long rangeEnd;
        final long chunkSize = 1024 * 1024; // 1MB chunk size

        if (headers.getRange() != null && !headers.getRange().isEmpty()) {
            HttpRange httpRange = headers.getRange().get(0);
            rangeStart = httpRange.getRangeStart(contentLength);
            rangeEnd = Math.min(rangeStart + chunkSize, contentLength - 1);
        } else {
            rangeEnd = Math.min(chunkSize, contentLength - 1);
        }

        long rangeLength = rangeEnd - rangeStart + 1;
        return new ResourceRegion(video, rangeStart, rangeLength);
    }
}

package com.kt.social.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.base-url}")
    private String baseUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
//        String resourceLocation = "file:" + uploadPath + "/";
//
//        // Ví dụ: http://localhost:8080/files/posts/abc.jpg
//        registry.addResourceHandler("/files/**")
//                .addResourceLocations(resourceLocation)
//                .setCachePeriod(3600);
    }
}
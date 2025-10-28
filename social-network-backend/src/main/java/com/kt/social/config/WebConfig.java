package com.kt.social.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Cho phép truy cập công khai file đã upload
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:uploads/");
    }
}
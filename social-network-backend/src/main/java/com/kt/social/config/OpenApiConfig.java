package com.kt.social.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.base-url}")
    private String baseUrl;

    @Bean
    public OpenAPI openAPIConfig() {
        return new OpenAPI()
                .info(new Info()
                        .title("Social Network AI Backend")
                        .version("1.0")
                        .description("""
                                API Documentation for Social AI Platform  
                                Includes authentication, user management, posts, comments, chat, and AI moderation.
                        """)
                        .contact(new Contact()
                                .name("Social Network AI Backend - Pham Tuan Khang")
                                .email("ptkhang17122005@gmail.com")
                                .url("https://github.com/KhangPham205/kt-social-network-ai-backend")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components().addSecuritySchemes("BearerAuth",
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .servers(List.of(
                        new Server().url(baseUrl + "/").description("Local Development Server")
                ));
    }
}

package com.kt.social.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.social.auth.security.JwtAuthenticationFilter;
import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.exception.ErrorResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.OutputStream;
import java.util.Date;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${frontend.base-url}")
    private String frontendBaseUrl;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 401 (UNAUTHORIZED)
    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401

            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Yêu cầu xác thực không hợp lệ hoặc bị thiếu.",
                    new Date().getTime()
            );

            // Ghi lỗi vào response body
            OutputStream out = response.getOutputStream();
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(out, errorResponse);
            out.flush();
        };
    }

    // 403 (FORBIDDEN)
    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403

            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.FORBIDDEN.value(),
                    "Bạn không có quyền truy cập tài nguyên này.",
                    new Date().getTime()
            );

            OutputStream out = response.getOutputStream();
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(out, errorResponse);
            out.flush();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )

                .authorizeHttpRequests(auth -> auth
                        // 1. Whitelist
                        .requestMatchers(ApiConstants.SWAGGER_WHITELIST).permitAll()
                        .requestMatchers(ApiConstants.PUBLIC_API_WHITELIST).permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()

                        // 2. Admin
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ADMIN:READ") // (Hoặc hasRole("ADMIN"))

                        // 3. User API
                        .requestMatchers(ApiConstants.USERS + "/**").authenticated()

                        // 4. Post API
                        .requestMatchers(HttpMethod.GET, ApiConstants.POSTS + "/**").authenticated()
                        .requestMatchers(HttpMethod.POST, ApiConstants.POSTS + "/create").hasAuthority("POST:CREATE")
                        .requestMatchers(HttpMethod.POST, ApiConstants.POSTS + "/share").hasAuthority("POST:CREATE")
                        .requestMatchers(HttpMethod.PUT, ApiConstants.POSTS + "/update").hasAuthority("POST:UPDATE")
                        .requestMatchers(HttpMethod.DELETE, ApiConstants.POSTS + "/**").hasAuthority("POST:DELETE")

                        // 5. Comment API
                        .requestMatchers(HttpMethod.GET, ApiConstants.COMMENTS + "/**").authenticated()
                        .requestMatchers(HttpMethod.POST, ApiConstants.COMMENTS + "/create").hasAuthority("COMMENT:CREATE")
                        .requestMatchers(HttpMethod.PUT, ApiConstants.COMMENTS + "/update").hasAuthority("COMMENT:UPDATE")
                        .requestMatchers(HttpMethod.DELETE, ApiConstants.COMMENTS + "/**").hasAuthority("COMMENT:DELETE")

                        // 6. Friendship API
                        .requestMatchers(ApiConstants.FRIENDSHIP + "/**").authenticated()

                        // 7. Report API
                        .requestMatchers(HttpMethod.POST, ApiConstants.REPORTS + "/**")
                        .hasAuthority("REPORT:CREATE")

                        // 8. Moderation API
                        .requestMatchers(HttpMethod.GET, ApiConstants.MODERATION + "/**")
                        .hasAuthority("MODERATION:READ")
                        .requestMatchers(HttpMethod.PUT, ApiConstants.MODERATION + "/**")
                        .hasAuthority("MODERATION:UPDATE")

                        // 9. Any other requests
                        .anyRequest().authenticated()
                )
                .addFilterBefore((Filter) jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of(
                frontendBaseUrl,              // 1. Origin cho App React/Vue (Production)
                "http://127.0.0.1:5500",      // 2. Origin cho file test HTML
                "http://localhost:5500",
                "ws://localhost:8080"        // 3. Origin cho WebSocket (local)
        ));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
package com.kt.social.infra.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public List<Float> getEmbedding(String text) {
        try {
            String url = aiServiceUrl + "/embed";

            // Tạo body request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("text", text);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            // Gọi API Python
            String response = restTemplate.postForObject(url, request, String.class);

            // Parse JSON response: {"vector": [0.1, 0.2, ...]}
            JsonObject jsonObject = gson.fromJson(response, JsonObject.class);
            JsonArray vectorArray = jsonObject.getAsJsonArray("vector");

            List<Float> vector = new ArrayList<>();
            for (int i = 0; i < vectorArray.size(); i++) {
                vector.add(vectorArray.get(i).getAsFloat());
            }
            return vector;

        } catch (Exception e) {
            log.error("Lỗi khi gọi AI Service: {}", e.getMessage());
            return new ArrayList<>(); // Trả về rỗng nếu lỗi
        }
    }

    /**
     * Kiểm tra nội dung độc hại
     * @return true nếu nội dung độc hại, false nếu an toàn
     */
    public boolean isContentToxic(String text) {
        if (text == null || text.isBlank()) return false;

        try {
            String url = aiServiceUrl + "/moderate";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("text", text);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(url, request, String.class);
            JsonObject json = gson.fromJson(response, JsonObject.class);

            if (json.has("is_toxic")) {
                boolean isToxic = json.get("is_toxic").getAsBoolean();
                if (isToxic) {
                    log.warn("🛡️ AI Moderation: Chặn nội dung độc hại. Flags: {}", json.get("flags"));
                }
                return isToxic;
            }
            return false;

        } catch (Exception e) {
            log.error("⚠️ Lỗi gọi AI Moderation: {}", e.getMessage());
            return false; // Fail-open: Nếu AI chết, tạm thời cho qua để không chặn nhầm
        }
    }
}
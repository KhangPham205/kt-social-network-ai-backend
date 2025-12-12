package com.kt.social.infra.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kt.social.domain.moderation.dto.ModerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
    public ModerationResult checkContentToxicity(String text) {
        if (text == null || text.isBlank())
            return new ModerationResult(false, null);

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
                String reason = null;

                if (isToxic) {
                    // Lấy danh sách cờ vi phạm (flags) để làm lý do
                    JsonArray flags = json.getAsJsonArray("flags");
                    reason = "Vi phạm tiêu chuẩn cộng đồng: " + flags.toString();
                    log.warn("🛡AI Moderation: Chặn nội dung. Reason: {}", reason);
                }

                return new ModerationResult(isToxic, reason);
            }
            return new ModerationResult(false, null);

        } catch (Exception e) {
            log.error("⚠️ Lỗi gọi AI Moderation: {}", e.getMessage());
            return new ModerationResult(false, null);        }
    }

    /**
     * Kiểm tra hình ảnh độc hại
     * @param imageBytes Mảng byte của ảnh
     * @param filename Tên file (để Python biết định dạng)
     */
    public ModerationResult checkImageToxicity(byte[] imageBytes, String filename) {
        try {
            String url = aiServiceUrl + "/moderate/image";

            // Tạo Header cho Multipart
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Tạo Body chứa file
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename; // Bắt buộc phải có tên file
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Gọi API Python
            String response = restTemplate.postForObject(url, requestEntity, String.class);
            JsonObject json = gson.fromJson(response, JsonObject.class);

            if (json.has("is_toxic")) {
                boolean isToxic = json.get("is_toxic").getAsBoolean();
                String reason = json.get("reason").getAsString();

                if (isToxic) {
                    log.warn("🛡️ AI Image Check: Chặn ảnh {}. Lý do: {}", filename, reason);
                }
                return new ModerationResult(isToxic, reason);
            }

            return new ModerationResult(false, null);

        } catch (Exception e) {
            log.error("⚠️ Lỗi gọi AI Image Moderation: {}", e.getMessage());
            return new ModerationResult(false, null);
        }
    }
}
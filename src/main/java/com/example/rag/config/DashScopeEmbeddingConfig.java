package com.example.rag.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class DashScopeEmbeddingConfig {

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${dashscope.embedding.model:text-embedding-v4}")
    private String model;

    @Value("${dashscope.embedding.dimensions:1536}")
    private int dimensions;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "dashscope.api-key", matchIfMissing = false)
    public EmbeddingModel dashScopeEmbeddingModel(RestClient.Builder restClientBuilder) {
        return new DashScopeEmbeddingModel(restClientBuilder, apiKey, model, dimensions);
    }

    static class DashScopeEmbeddingModel implements EmbeddingModel {

        private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        private final RestClient restClient;
        private final String apiKey;
        private final String model;
        private final int dimensions;
        private final ObjectMapper objectMapper;

        DashScopeEmbeddingModel(RestClient.Builder builder, String apiKey, String model, int dimensions) {
            this.restClient = builder.baseUrl(BASE_URL).build();
            this.apiKey = apiKey;
            this.model = model;
            this.dimensions = dimensions;
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public float[] embed(String text) {
            if (text == null || text.isEmpty()) {
                return new float[dimensions];
            }
            try {
                var request = Map.of(
                        "model", model,
                        "input", text,
                        "dimensions", dimensions,
                        "encoding_format", "float"
                );
                var response = restClient.post()
                        .uri("/embeddings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + apiKey)
                        .body(request)
                        .retrieve()
                        .body(DashScopeEmbeddingResponse.class);

                if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                    var embedding = response.getData().get(0).getEmbedding();
                    float[] result = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        result[i] = embedding.get(i).floatValue();
                    }
                    log.debug("DashScope embedding generated, dimension={}", result.length);
                    return result;
                }
            } catch (Exception e) {
                log.error("DashScope embedding API failed: {}", e.getMessage());
            }
            return new float[dimensions];
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        @Override
        public EmbeddingResponse embedForResponse(List<String> texts) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(new Embedding(embed(texts.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return embedForResponse(request.getInstructions());
        }

        @Override
        public int dimensions() {
            return dimensions;
        }
    }

    @Data
    static class DashScopeEmbeddingResponse {
        private List<EmbeddingData> data;
        private String model;
        private Usage usage;
    }

    @Data
    static class EmbeddingData {
        private List<Double> embedding;
        private int index;
    }

    @Data
    static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}

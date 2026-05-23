package com.example.rag.config;

import com.fasterxml.jackson.annotation.JsonProperty;
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

/**
 * 阿里云 DashScope 向量嵌入配置
 * <p>
 * 使用 DashScope 兼容模式 API（OpenAI 兼容）提供 EmbeddingModel 实现。
 * 当配置了 {@code dashscope.api-key} 属性时，该 Bean 会作为 {@code @Primary} 注入，
 * 覆盖 Spring AI 默认的 OpenAI Embedding。
 * </p>
 *
 * <h3>请求格式</h3>
 * <pre>
 * POST https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
 * Body: { "model": "text-embedding-v4", "input": "...", "dimensions": 1536, "encoding_format": "float" }
 * </pre>
 *
 * <h3>重试策略</h3>
 * 单次调用最多重试 3 次，采用指数退避（1s → 2s → 4s）。
 * 全部失败后抛异常，由上层 {@code VectorService} 的 {@code @Retryable} 兜底。
 */
@Slf4j
@Configuration
public class DashScopeEmbeddingConfig {

    /** 阿里云 DashScope API Key（环境变量 DASHSCOPE_API_KEY） */
    @Value("${dashscope.api-key:}")
    private String apiKey;

    /** 嵌入模型名称，默认为 text-embedding-v4（1024/1536 维） */
    @Value("${dashscope.embedding.model:text-embedding-v4}")
    private String model;

    /** 输出向量维度，默认 1536 */
    @Value("${dashscope.embedding.dimensions:1536}")
    private int dimensions;

    /**
     * 创建 DashScope EmbeddingModel Bean
     * <p>
     * 仅当配置了 dashscope.api-key 时生效，作为 @Primary 注入到 Spring 容器。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "dashscope.api-key")
    public EmbeddingModel dashScopeEmbeddingModel(RestClient.Builder restClientBuilder) {
        return new DashScopeEmbeddingModel(restClientBuilder, apiKey, model, dimensions);
    }

    // ==================== EmbeddingModel 实现 ====================

    /**
     * 基于 DashScope 兼容模式 API 的 EmbeddingModel 实现
     */
    static class DashScopeEmbeddingModel implements EmbeddingModel {

        /** DashScope 兼容模式基础地址 */
        private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        /** 最大重试次数 */
        private static final int MAX_RETRIES = 3;

        /** 初始退避延迟（毫秒） */
        private static final long INITIAL_BACKOFF_MS = 1000;

        private final RestClient restClient;
        private final String apiKey;
        private final String model;
        private final int dimensions;

        DashScopeEmbeddingModel(RestClient.Builder builder, String apiKey, String model, int dimensions) {
            this.restClient = builder.baseUrl(BASE_URL).build();
            this.apiKey = apiKey;
            this.model = model;
            this.dimensions = dimensions;
        }

        // ---------- 单文本向量化 ----------

        @Override
        public float[] embed(String text) {
            if (text == null || text.isEmpty()) {
                return new float[dimensions];
            }
            return doEmbedWithRetry(text);
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        // ---------- 批量向量化 ----------

        /**
         * 对多个文本进行向量化
         * <p>
         * 遍历每个文本独立调用 API。如果需高性能场景，可改为单请求传入 texts 数组。
         */
        @Override
        public EmbeddingResponse embedForResponse(List<String> texts) {
            List<Embedding> embeddings = new ArrayList<>(texts.size());
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

        // ==================== 核心：带重试的 API 调用 ====================

        /**
         * 调用 DashScope Embedding API，失败自动重试
         *
         * @param text 待向量化的文本
         * @return 向量数组（float[]，维度 = {@link #dimensions}）
         * @throws RuntimeException 全部重试仍失败时抛出，携带最后一次错误信息
         */
        private float[] doEmbedWithRetry(String text) {
            int attempts = 0;
            long delayMs = INITIAL_BACKOFF_MS;
            Exception lastException = null;

            while (attempts < MAX_RETRIES) {
                try {
                    attempts++;
                    return callEmbeddingApi(text, attempts);
                } catch (Exception e) {
                    lastException = e;
                    log.warn("DashScope embedding attempt {}/{} failed: {}",
                            attempts, MAX_RETRIES, e.getMessage());
                    if (attempts >= MAX_RETRIES) {
                        break;
                    }
                    delayMs = sleepWithBackoff(delayMs);
                }
            }

            String errMsg = String.format(
                    "DashScope embedding failed after %d attempts. Last error: %s",
                    MAX_RETRIES,
                    lastException != null ? lastException.getMessage() : "unknown");
            throw new RuntimeException(errMsg, lastException);
        }

        // ==================== API 请求构建与发送 ====================

        /**
         * 构建请求体并调用 DashScope /embeddings 接口
         *
         * @param text     待向量化的文本
         * @param attempts 当前第几次尝试（仅用于日志）
         * @return 向量数组
         */
        private float[] callEmbeddingApi(String text, int attempts) {
            Map<String, Object> requestBody = buildRequestBody(text);

            DashScopeEmbeddingResponse apiResponse = restClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                byte[] body = res.getBody().readAllBytes();
                                String errorBody = new String(body);
                                log.error("DashScope API error [{}]: {}", res.getStatusCode(), errorBody);
                                throw new RuntimeException(
                                        "DashScope API error [" + res.getStatusCode() + "]: " + errorBody);
                            })
                    .body(DashScopeEmbeddingResponse.class);

            if (apiResponse == null
                    || apiResponse.getData() == null
                    || apiResponse.getData().isEmpty()) {
                throw new RuntimeException("DashScope returned empty response");
            }

            List<Double> rawEmbedding = apiResponse.getData().get(0).getEmbedding();
            float[] result = convertToFloatArray(rawEmbedding);

            log.info("DashScope embedding success: dimension={}, attempts={}", result.length, attempts);
            return result;
        }

        /**
         * 构建 DashScope 兼容模式请求体
         */
        private Map<String, Object> buildRequestBody(String text) {
            return Map.of(
                    "model", model,
                    "input", text,
                    "dimensions", dimensions,
                    "encoding_format", "float"
            );
        }

        // ==================== 工具方法 ====================

        /**
         * 将 List&lt;Double&gt; 转换为 float[]
         */
        private static float[] convertToFloatArray(List<Double> embedding) {
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
        }

        /**
         * 指数退避休眠
         *
         * @param delayMs 当前延迟
         * @return 翻倍后的延迟
         */
        private static long sleepWithBackoff(long delayMs) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return delayMs * 2;
        }
    }

    // ==================== API 响应 DTO ====================

    /**
     * DashScope 嵌入接口响应体
     * <pre>
     * {
     *   "data": [{ "embedding": [...], "index": 0 }],
     *   "model": "text-embedding-v4",
     *   "usage": { "prompt_tokens": 2, "total_tokens": 2 }
     * }
     * </pre>
     */
    @Data
    static class DashScopeEmbeddingResponse {
        private List<EmbeddingData> data;
        private String model;
        private Usage usage;
    }

    /**
     * 单个嵌入数据项
     */
    @Data
    static class EmbeddingData {
        /** 浮点向量列表，1536 维 */
        private List<Double> embedding;
        /** 数据索引 */
        private int index;
    }

    /**
     * Token 用量统计
     */
    @Data
    static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}

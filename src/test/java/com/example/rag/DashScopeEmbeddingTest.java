package com.example.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@SpringBootTest
public class DashScopeEmbeddingTest {

    @Autowired(required = false)
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @Test
    public void testViaSpringBean() {
        log.info("=== 通过 Spring Bean 调用 EmbeddingModel ===");

        if (embeddingModel == null) {
            log.error("❌ EmbeddingModel Bean 未注入！");
            log.error("可能原因：");
            log.error("  1. dashScopeEmbeddingModel 的 @ConditionalOnProperty 条件不满足");
            log.error("  2. spring.ai.openai.embedding.enabled=false 导致 OpenAI Embedding 也被禁用");
            log.error("  3. 没有任何可用的 EmbeddingModel Bean");
            return;
        }

        log.info("EmbeddingModel 类型: {}", embeddingModel.getClass().getName());
        log.info("EmbeddingModel dimensions: {}", embeddingModel.dimensions());

        try {
            float[] result = embeddingModel.embed("你好世界");
            log.info("✅ 成功！向量维度: {}, 前5个值: [{}, {}, {}, {}, {}]",
                    result.length,
                    String.format("%.4f", result[0]),
                    String.format("%.4f", result[1]),
                    String.format("%.4f", result[2]),
                    String.format("%.4f", result[3]),
                    String.format("%.4f", result[4]));
        } catch (Exception e) {
            log.error("❌ Bean 调用失败: {}", e.getMessage());
            Throwable cause = e;
            int level = 0;
            while (cause != null && level < 5) {
                log.error("  [{}] {}: {}", level, cause.getClass().getSimpleName(), cause.getMessage());
                cause = cause.getCause();
                level++;
            }
        }
    }

    /**
     * 直接用 RestClient 测试 DashScope Embedding API，绕过 Spring Bean 体系，
     * 查看原始请求和响应的完整信息。
     *
     * 运行方式：在 IDE 中右键运行此方法
     */
    @Test
    public void testEmbeddingRaw() {
        String apiKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
        String model = "text-embedding-v4";
        int dimensions = 1536;

        log.info("=== DashScope Embedding 诊断测试开始 ===");
        log.info("API Key 长度: {}", apiKey.length());
        log.info("API Key 前缀: {}...", apiKey.isEmpty() ? "(空)" : apiKey.substring(0, Math.min(8, apiKey.length())));
        log.info("Model: {}", model);
        log.info("Dimensions: {}", dimensions);

        if (apiKey.isEmpty() || "your-dashscope-api-key".equals(apiKey)) {
            log.error("❌ API Key 未设置或仍是默认占位值！");
            log.error("请在环境变量中设置: export DASHSCOPE_API_KEY=sk-xxx");
            return;
        }

        String text = "你好世界";

        try {
            RestClient client = RestClient.builder().baseUrl(BASE_URL).build();

            var request = Map.of(
                    "model", model,
                    "input", text,
                    "dimensions", dimensions,
                    "encoding_format", "float"
            );

            log.info("请求体: {}", new ObjectMapper().writeValueAsString(request));

            var response = client.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("✅ 成功！响应前200字符:");
            if (response != null && response.length() > 200) {
                log.info(response.substring(0, 200) + "...");
            } else {
                log.info(String.valueOf(response));
            }
        } catch (Exception e) {
            log.error("❌ API 调用失败: {}", e.getMessage());

            // Deep dive into the cause
            Throwable cause = e;
            while (cause != null) {
                log.error("  Caused by: {} - {}", cause.getClass().getSimpleName(), cause.getMessage());
                cause = cause.getCause();
            }
        }
    }

    /**
     * 测试 API Key 为空时的行为
     */
    @Test
    public void testWithoutApiKey() {
        log.info("=== 无 API Key 测试 ===");

        try {
            RestClient client = RestClient.builder().baseUrl(BASE_URL).build();

            var request = Map.of(
                    "model", "text-embedding-v4",
                    "input", "hello",
                    "dimensions", 1536,
                    "encoding_format", "float"
            );

            String response = client.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer invalid-key-123")
                    .body(request)
                    .retrieve()
                    .body(String.class);

            log.info("Response: {}", response);
        } catch (Exception e) {
            log.error("预期失败: {}", e.getMessage());
            Throwable cause = e;
            while (cause != null) {
                log.error("  Caused by: {} - {}", cause.getClass().getSimpleName(), cause.getMessage());
                cause = cause.getCause();
            }
        }
    }
}

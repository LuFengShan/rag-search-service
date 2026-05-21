package com.example.rag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorServiceTest {

    private static final int VECTOR_DIMENSION = 1536;

    private VectorService createVectorService() {
        EmbeddingModel mockModel = new EmbeddingModel() {
            @Override
            public float[] embed(String text) {
                float[] embedding = new float[VECTOR_DIMENSION];
                int hash = text != null ? text.hashCode() : 0;
                for (int i = 0; i < VECTOR_DIMENSION; i++) {
                    embedding[i] = (float) (Math.sin(i * hash * 0.01) * 0.5 + 0.5);
                }
                return embedding;
            }

            @Override
            public float[] embed(Document document) {
                return embed(document.getText());
            }

            @Override
            public EmbeddingResponse embedForResponse(List<String> texts) {
                return null;
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return null;
            }

            @Override
            public int dimensions() {
                return VECTOR_DIMENSION;
            }
        };
        return new VectorService(null, null, mockModel);
    }

    // ==================== 向量生成测试 ====================

    @Test
    @DisplayName("测试生成向量维度")
    void testEmbed_Dimension() {
        VectorService vectorService = createVectorService();
        String text = "测试文本";
        float[] embedding = vectorService.embed(text);

        assertNotNull(embedding);
        assertEquals(1536, embedding.length, "向量维度应该是1536");
    }

    @Test
    @DisplayName("测试相同文本生成相同向量")
    void testEmbed_Deterministic() {
        VectorService vectorService = createVectorService();
        String text = "相同的测试文本";

        float[] embedding1 = vectorService.embed(text);
        float[] embedding2 = vectorService.embed(text);

        assertNotNull(embedding1);
        assertNotNull(embedding2);
        assertEquals(embedding1.length, embedding2.length);

        for (int i = 0; i < embedding1.length; i++) {
            assertEquals(embedding1[i], embedding2[i], 0.0001f,
                "相同文本应该生成相同的向量");
        }
    }

    @Test
    @DisplayName("测试不同文本生成不同向量")
    void testEmbed_DifferentTexts() {
        VectorService vectorService = createVectorService();
        String text1 = "第一个测试文本";
        String text2 = "第二个测试文本";

        float[] embedding1 = vectorService.embed(text1);
        float[] embedding2 = vectorService.embed(text2);

        assertNotNull(embedding1);
        assertNotNull(embedding2);

        boolean areDifferent = false;
        for (int i = 0; i < Math.min(embedding1.length, 100); i++) {
            if (Math.abs(embedding1[i] - embedding2[i]) > 0.01f) {
                areDifferent = true;
                break;
            }
        }
        assertTrue(areDifferent, "不同文本应该生成不同的向量");
    }

    @Test
    @DisplayName("测试向量值范围")
    void testEmbed_ValueRange() {
        VectorService vectorService = createVectorService();
        String text = "测试文本内容";

        float[] embedding = vectorService.embed(text);

        for (int i = 0; i < embedding.length; i++) {
            assertTrue(embedding[i] >= 0.0f && embedding[i] <= 1.0f,
                "向量值应该在[0, 1]范围内，当前值: " + embedding[i]);
        }
    }

    @Test
    @DisplayName("测试空文本生成向量")
    void testEmbed_EmptyText() {
        VectorService vectorService = createVectorService();
        String text = "";

        float[] embedding = vectorService.embed(text);

        assertNotNull(embedding);
        assertEquals(1536, embedding.length);
    }

    @Test
    @DisplayName("测试长文本生成向量")
    void testEmbed_LongText() {
        VectorService vectorService = createVectorService();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("长文本测试").append(i).append(" ");
        }
        String text = sb.toString();

        float[] embedding = vectorService.embed(text);

        assertNotNull(embedding);
        assertEquals(1536, embedding.length);
    }

    @Test
    @DisplayName("测试特殊字符文本生成向量")
    void testEmbed_SpecialCharacters() {
        VectorService vectorService = createVectorService();
        String text = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\n\t\r";

        float[] embedding = vectorService.embed(text);

        assertNotNull(embedding);
        assertEquals(1536, embedding.length);
    }

    @Test
    @DisplayName("测试中英文混合文本生成向量")
    void testEmbed_MixedLanguages() {
        VectorService vectorService = createVectorService();
        String text = "Hello World！你好世界！123";

        float[] embedding = vectorService.embed(text);

        assertNotNull(embedding);
        assertEquals(1536, embedding.length);
    }

    // ==================== 向量格式转换测试 ====================

    @Test
    @DisplayName("测试向量数组转换")
    void testArrayToString_Simple() throws Exception {
        VectorService vectorService = createVectorService();
        java.lang.reflect.Method method = VectorService.class.getDeclaredMethod(
            "arrayToString", float[].class);
        method.setAccessible(true);

        float[] array = {0.1f, 0.2f, 0.3f};
        String result = (String) method.invoke(vectorService, (Object) array);

        assertNotNull(result);
        assertEquals("[0.1, 0.2, 0.3]", result);
    }

    @Test
    @DisplayName("测试空数组转换")
    void testArrayToString_EmptyArray() throws Exception {
        VectorService vectorService = createVectorService();
        java.lang.reflect.Method method = VectorService.class.getDeclaredMethod(
            "arrayToString", float[].class);
        method.setAccessible(true);

        float[] array = {};
        String result = (String) method.invoke(vectorService, (Object) array);

        assertNotNull(result);
        assertEquals("[]", result);
    }

    @Test
    @DisplayName("测试单元素数组转换")
    void testArrayToString_SingleElement() throws Exception {
        VectorService vectorService = createVectorService();
        java.lang.reflect.Method method = VectorService.class.getDeclaredMethod(
            "arrayToString", float[].class);
        method.setAccessible(true);

        float[] array = {1.0f};
        String result = (String) method.invoke(vectorService, (Object) array);

        assertNotNull(result);
        assertEquals("[1.0]", result);
    }

    @Test
    @DisplayName("测试1536维度向量转换")
    void testArrayToString_FullDimension() throws Exception {
        VectorService vectorService = createVectorService();
        java.lang.reflect.Method method = VectorService.class.getDeclaredMethod(
            "arrayToString", float[].class);
        method.setAccessible(true);

        float[] array = new float[1536];
        for (int i = 0; i < 1536; i++) {
            array[i] = (float) (Math.sin(i) * 0.5 + 0.5);
        }

        String result = (String) method.invoke(vectorService, (Object) array);

        assertNotNull(result);
        assertTrue(result.startsWith("[0.5"));
        assertTrue(result.endsWith("]"));
    }

    // ==================== 向量相似度计算测试 ====================

    @Test
    @DisplayName("测试完全相同向量的相似度")
    void testCosineSimilarity_IdenticalVectors() {
        VectorService vectorService = createVectorService();
        String text = "测试文本";
        float[] embedding1 = vectorService.embed(text);
        float[] embedding2 = vectorService.embed(text);

        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        double similarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));

        assertEquals(1.0, similarity, 0.0001, "完全相同的向量相似度应该是1");
    }

    @Test
    @DisplayName("测试不同文本的相似度差异")
    void testCosineSimilarity_DifferentTexts() {
        VectorService vectorService = createVectorService();
        float[] embedding1 = vectorService.embed("文本一");
        float[] embedding2 = vectorService.embed("文本二");

        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        double similarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));

        assertTrue(similarity > 0 && similarity <= 1,
            "不同文本的相似度应该在(0, 1]范围内");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("测试null文本")
    void testEmbed_NullText() {
        VectorService vectorService = createVectorService();
        float[] embedding = vectorService.embed(null);

        assertNotNull(embedding);
        assertEquals(1536, embedding.length);
    }

    @Test
    @DisplayName("测试emoji和特殊Unicode")
    void testEmbed_Emoji() {
        VectorService vectorService = createVectorService();
        String text = "😀🎉💻🚀";

        float[] embedding = vectorService.embed(text);

        assertNotNull(embedding);
        assertEquals(1536, embedding.length);
    }

    @Test
    @DisplayName("测试非常长的文本")
    void testEmbed_VeryLongText() {
        VectorService vectorService = createVectorService();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("重复文本");
        }
        String text = sb.toString();

        float[] embedding = vectorService.embed(text);

        assertNotNull(embedding);
        assertEquals(1536, embedding.length);
    }

    // ==================== 性能测试 ====================

    @Test
    @DisplayName("测试批量生成向量性能")
    void testEmbed_BatchPerformance() {
        VectorService vectorService = createVectorService();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            String text = "批量测试文本" + i;
            vectorService.embed(text);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertTrue(duration < 5000,
            "100次向量生成应该在5秒内完成，实际: " + duration + "ms");
    }
}

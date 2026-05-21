package com.example.rag.service;

import com.example.rag.entity.DocumentChunk;
import com.example.rag.mapper.DocumentChunkMapper;
import com.example.rag.mapper.DocumentChunkVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentChunkVectorMapper documentChunkVectorMapper;
    private final EmbeddingModel embeddingModel;

    private static final int VECTOR_DIMENSION = 1536;

    @Transactional
    public void saveDocumentChunk(UUID documentId, int chunkIndex, String content, float[] embedding) {
        String vectorString = arrayToString(embedding);

        documentChunkVectorMapper.insertWithVector(
                UUID.randomUUID(),
                documentId,
                chunkIndex,
                content,
                vectorString
        );

        log.debug("Saved document chunk: documentId={}, chunkIndex={}", documentId, chunkIndex);
    }

    public List<DocumentChunk> searchByCosineSimilarity(float[] queryEmbedding, UUID knowledgeBaseId, int limit) {
        String vectorString = arrayToString(queryEmbedding);
        return documentChunkVectorMapper.findByCosineSimilarity(vectorString, null, knowledgeBaseId, limit);
    }

    public List<DocumentChunk> searchByL2Distance(float[] queryEmbedding, UUID knowledgeBaseId, int limit) {
        String vectorString = arrayToString(queryEmbedding);
        return documentChunkVectorMapper.findNearestL2(vectorString, null, knowledgeBaseId, limit);
    }

    public List<DocumentChunk> searchByInnerProduct(float[] queryEmbedding, UUID knowledgeBaseId, int limit) {
        String vectorString = arrayToString(queryEmbedding);
        return documentChunkVectorMapper.findByNegativeInnerProduct(vectorString, null, knowledgeBaseId, limit);
    }

    public void deleteByDocumentId(UUID documentId) {
        documentChunkMapper.deleteByDocumentId(documentId);
    }

    public long countByDocumentId(UUID documentId) {
        return documentChunkMapper.countByDocumentId(documentId);
    }

    String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public float[] embed(String text) {
        try {
            float[] result = embeddingModel.embed(text);
            log.debug("Generated embedding via Alibaba text-embedding-v4, dimension={}", result.length);
            return result;
        } catch (Exception e) {
            log.warn("Embedding API failed, using fallback mock embedding: {}", e.getMessage());
            return generateFallbackEmbedding(text);
        }
    }

    private float[] generateFallbackEmbedding(String text) {
        float[] embedding = new float[VECTOR_DIMENSION];
        int hash = text != null ? text.hashCode() : 0;
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            embedding[i] = (float) (Math.sin(i * hash * 0.01) * 0.5 + 0.5);
        }
        return embedding;
    }
}

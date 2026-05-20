package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.DocumentChunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface DocumentChunkVectorMapper extends BaseMapper<DocumentChunk> {

    @Insert("INSERT INTO document_chunk (id, document_id, chunk_index, content, embedding, created_at) " +
            "VALUES (#{id}, #{documentId}, #{chunkIndex}, #{content}, CAST(#{embedding} AS vector), NOW())")
    void insertWithVector(@Param("id") UUID id,
                          @Param("documentId") UUID documentId,
                          @Param("chunkIndex") int chunkIndex,
                          @Param("content") String content,
                          @Param("embedding") String embedding);

    List<DocumentChunk> findNearest(@Param("queryVector") String queryVector,
                                   @Param("documentId") UUID documentId,
                                   @Param("limit") int limit);

    List<DocumentChunk> findNearestL2(@Param("queryVector") String queryVector,
                                      @Param("documentId") UUID documentId,
                                      @Param("limit") int limit);

    List<DocumentChunk> findByCosineSimilarity(@Param("queryVector") String queryVector,
                                               @Param("documentId") UUID documentId,
                                               @Param("limit") int limit);

    List<DocumentChunk> findByNegativeInnerProduct(@Param("queryVector") String queryVector,
                                                    @Param("documentId") UUID documentId,
                                                    @Param("limit") int limit);

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(@Param("documentId") UUID documentId);
}
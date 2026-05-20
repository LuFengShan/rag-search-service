package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.entity.Document;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.UUID;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    default IPage<Document> findByKnowledgeBaseId(Page<Document> page, UUID knowledgeBaseId) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId));
    }

    default IPage<Document> findByTitleContaining(Page<Document> page, String title) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .like(Document::getTitle, title));
    }

    default IPage<Document> findByKnowledgeBaseIdAndTitleContaining(Page<Document> page, UUID knowledgeBaseId, String title) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .like(Document::getTitle, title));
    }

    default List<Document> findByUploadedBy(UUID uploadedBy) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getUploadedBy, uploadedBy));
    }

    default long countByKnowledgeBaseId(UUID knowledgeBaseId) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId));
    }
}
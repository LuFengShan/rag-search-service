package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.UUID;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    default List<KnowledgeBase> findByCreatedBy(UUID createdBy) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getCreatedBy, createdBy));
    }

    default List<KnowledgeBase> findByNameContaining(String name) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                .like(KnowledgeBase::getName, name));
    }
}
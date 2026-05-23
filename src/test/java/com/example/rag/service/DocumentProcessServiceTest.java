package com.example.rag.service;

import com.example.rag.document.MarkdownFrontmatterExtractor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class DocumentProcessServiceTest {

    @Test
    void splitByHeading2_shouldProduceSixChapters() throws Exception {
        String content = Files.readString(Paths.get("docs/car-docs/丰田-凯美瑞.md"));
        String body = MarkdownFrontmatterExtractor.extractBody(content);

        log.info("body length: {}", body != null ? body.length() : 0);
        List<String> chapters = DocumentProcessService.splitByHeading2(body);

        log.info("total chapters: {}", chapters.size());
        chapters.forEach(c -> log.info("  {}", c.lines().findFirst().orElse("(empty)")));

        assertThat(chapters).isNotNull();
        assertThat(chapters).hasSize(6);
        chapters.forEach(ch -> assertThat(ch).startsWith("## "));
    }

    @Test
    void chunkOutput_shouldContainMetadata() throws Exception {
        String content = Files.readString(Paths.get("docs/car-docs/丰田-凯美瑞.md"));
        var meta = MarkdownFrontmatterExtractor.extractCarMetadata(content);
        String body = MarkdownFrontmatterExtractor.extractBody(content);

        List<String> chapters = DocumentProcessService.splitByHeading2(body);
        String tagsStr = meta.getTags() != null ? String.join("、", meta.getTags()) : "";
        String compStr = meta.getCompetitors() != null ? String.join("、", meta.getCompetitors()) : "";
        String metaPrefix = String.join("\n",
                "【品牌:" + meta.getBrand() + "/" + meta.getModel() + "】",
                "【价格:" + meta.getPriceRange() + "】",
                "【标签:" + tagsStr + "】",
                "【竞品:" + compStr + "】");

        for (int i = 0; i < chapters.size(); i++) {
            String chunk = metaPrefix + "\n\n" + chapters.get(i);
            log.info("Chunk {}: {} chars, starts with: {}", i + 1, chunk.length(),
                    chunk.substring(0, Math.min(80, chunk.length())));
            assertThat(chunk).contains("【品牌:");
            assertThat(chunk.length()).isLessThan(6000);
        }
    }
}

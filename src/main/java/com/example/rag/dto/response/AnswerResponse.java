
package com.example.rag.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResponse {

    private String answer;

    private List<SourceInfo> sources;

    private Float confidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceInfo {
        private UUID documentId;
        private String documentTitle;
        private String chunkContent;
        private Float confidence;
    }
}

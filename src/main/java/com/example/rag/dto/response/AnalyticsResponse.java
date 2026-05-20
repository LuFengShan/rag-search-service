
package com.example.rag.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    private long totalQuestions;

    private double avgResponseTime;

    private double satisfactionRate;

    private List<HotDocument> hotDocuments;

    private List<HotQuestion> hotQuestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotDocument {
        private String id;
        private String title;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotQuestion {
        private String question;
        private long count;
    }
}

package com.example.rag.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量上传响应
 * <p>
 * 返回批量上传的汇总信息，包含成功/失败数量和详情。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadResponse {

    /** 上传文件总数 */
    private int totalFiles;

    /** 成功上传的文件数 */
    private int successCount;

    /** 失败的文件数 */
    private int failCount;

    /** 成功上传的文档列表 */
    @Builder.Default
    private List<DocumentResponse> successList = new ArrayList<>();

    /** 失败详情列表 */
    @Builder.Default
    private List<UploadError> errors = new ArrayList<>();

    /**
     * 单个上传失败详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadError {
        /** 失败的文件名 */
        private String fileName;
        /** 失败原因 */
        private String reason;
    }
}

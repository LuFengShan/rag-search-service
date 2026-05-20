
package com.example.rag.controller;

import com.example.rag.dto.response.AnalyticsResponse;
import com.example.rag.dto.response.ApiResponse;
import com.example.rag.dto.response.TrendResponse;
import com.example.rag.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "运营分析", description = "统计分析接口（管理员权限）")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController extends BaseController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "获取统计概览", description = "获取问答量、响应时间、满意度等统计数据")
    public ResponseEntity<ApiResponse<AnalyticsResponse>> getOverview() {
        AnalyticsResponse response = analyticsService.getOverview();
        return ResponseEntity.ok(success(response));
    }

    @GetMapping("/trend")
    @Operation(summary = "获取趋势数据", description = "获取指定时间范围的问答趋势")
    public ResponseEntity<ApiResponse<TrendResponse>> getTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "daily") String type) {
        TrendResponse response = analyticsService.getTrend(startDate, endDate, type);
        return ResponseEntity.ok(success(response));
    }
}

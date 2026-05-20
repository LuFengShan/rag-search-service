
package com.example.rag.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        
        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();
        String queryString = httpRequest.getQueryString();
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("[REQUEST] {} {} {} | Client: {} | User-Agent: {} | Time: {}", 
                    method, 
                    uri + (queryString != null ? "?" + queryString : ""),
                    requestId,
                    clientIp,
                    userAgent != null ? userAgent.substring(0, Math.min(100, userAgent.length())) : "N/A",
                    LocalDateTime.now().format(FORMATTER));
            
            chain.doFilter(request, response);
            
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = httpResponse.getStatus();
            
            log.info("[RESPONSE] {} {} {} | Status: {} | Duration: {}ms | Time: {}",
                    method,
                    uri,
                    requestId,
                    statusCode,
                    duration,
                    LocalDateTime.now().format(FORMATTER));
                    
            logAccessLog(method, uri, clientIp, userAgent, statusCode, duration, requestId);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[ERROR] {} {} {} | Duration: {}ms | Exception: {}",
                    method,
                    uri,
                    requestId,
                    duration,
                    e.getMessage());
            throw e;
        } finally {
            MDC.remove("requestId");
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    private void logAccessLog(String method, String uri, String clientIp, String userAgent, 
                             int statusCode, long duration, String requestId) {
        String logEntry = String.format(
            "%s | %s | %s | %s | %d | %dms | %s | %s",
            LocalDateTime.now().format(FORMATTER),
            clientIp,
            method,
            uri,
            statusCode,
            duration,
            requestId,
            userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "N/A"
        );
        
        log.info("[ACCESS] {}", logEntry);
    }
}

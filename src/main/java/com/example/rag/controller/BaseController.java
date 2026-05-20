package com.example.rag.controller;

import com.example.rag.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class BaseController {

    protected Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    protected String getCurrentUsername() {
        Authentication auth = getCurrentAuthentication();
        return auth != null ? auth.getName() : null;
    }

    protected UUID getCurrentUserId() {
        Authentication auth = getCurrentAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.example.rag.security.CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        return null;
    }

    protected boolean hasRole(String role) {
        Authentication auth = getCurrentAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    protected boolean isAdmin() {
        return hasRole("ADMIN");
    }

    protected boolean isKnowledgeBaseAdmin() {
        return hasRole("KNOWLEDGE_BASE_ADMIN") || isAdmin();
    }

    protected <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data);
    }

    protected <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.success(message, data);
    }
}
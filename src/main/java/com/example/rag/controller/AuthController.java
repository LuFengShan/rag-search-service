
package com.example.rag.controller;

import com.example.rag.dto.request.CreateUserRequest;
import com.example.rag.dto.request.LoginRequest;
import com.example.rag.dto.request.RefreshTokenRequest;
import com.example.rag.dto.response.ApiResponse;
import com.example.rag.dto.response.LoginResponse;
import com.example.rag.dto.response.UserResponse;
import com.example.rag.entity.User;
import com.example.rag.exception.BusinessException;
import com.example.rag.security.JwtTokenProvider;
import com.example.rag.service.RefreshTokenService;
import com.example.rag.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录和注册接口")
public class AuthController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "通过用户名和密码登录系统")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新用户")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("注册成功", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用 refresh token 获取新的 access token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        
        if (!refreshTokenService.validateRefreshToken(refreshToken)) {
            throw new BusinessException("无效的刷新令牌");
        }

        String userId = refreshTokenService.getUserIdFromRefreshToken(refreshToken);
        User user = userService.getUserEntityById(UUID.fromString(userId));

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), 
                user.getUsername(), 
                user.getRole().name()
        );
        
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());
        
        refreshTokenService.invalidateRefreshToken(refreshToken);
        refreshTokenService.saveRefreshToken(user.getId().toString(), newRefreshToken);

        log.info("Token refreshed successfully for user: {}", user.getUsername());

        LoginResponse response = LoginResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok(ApiResponse.success("令牌刷新成功", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "使当前用户的所有 refresh token 失效")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader("Authorization") String token) {
        if (token != null && token.startsWith("Bearer ")) {
            String accessToken = token.substring(7);
            String userId = jwtTokenProvider.getUserIdFromToken(accessToken);
            refreshTokenService.invalidateAllRefreshTokens(userId);
            log.info("User logged out successfully: {}", userId);
        }
        return ResponseEntity.ok(ApiResponse.success("登出成功", null));
    }
}

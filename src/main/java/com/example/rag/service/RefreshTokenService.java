package com.example.rag.service;

import com.example.rag.exception.BusinessException;
import com.example.rag.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String USER_REFRESH_TOKENS_PREFIX = "user_refresh_tokens:";

    public void saveRefreshToken(String userId, String refreshToken) {
        long expiration = jwtTokenProvider.getRefreshExpiration();
        
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        String userTokensKey = USER_REFRESH_TOKENS_PREFIX + userId;
        
        redisTemplate.opsForValue().set(tokenKey, userId, expiration, TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add(userTokensKey, refreshToken);
        redisTemplate.expire(userTokensKey, expiration, TimeUnit.MILLISECONDS);
        
        log.debug("Refresh token saved for user: {}", userId);
    }

    public boolean validateRefreshToken(String refreshToken) {
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey));
    }

    public String getUserIdFromRefreshToken(String refreshToken) {
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        return redisTemplate.opsForValue().get(tokenKey);
    }

    public void invalidateRefreshToken(String refreshToken) {
        String userId = getUserIdFromRefreshToken(refreshToken);
        
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        redisTemplate.delete(tokenKey);
        
        if (userId != null) {
            String userTokensKey = USER_REFRESH_TOKENS_PREFIX + userId;
            redisTemplate.opsForSet().remove(userTokensKey, refreshToken);
        }
        
        log.debug("Refresh token invalidated");
    }

    public void invalidateAllRefreshTokens(String userId) {
        String userTokensKey = USER_REFRESH_TOKENS_PREFIX + userId;
        
        var refreshTokens = redisTemplate.opsForSet().members(userTokensKey);
        if (refreshTokens != null) {
            for (String refreshToken : refreshTokens) {
                String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
                redisTemplate.delete(tokenKey);
            }
            redisTemplate.delete(userTokensKey);
        }
        
        log.debug("All refresh tokens invalidated for user: {}", userId);
    }
}
package com.example.rag.service;

import com.example.rag.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 刷新令牌服务
 * <p>
 * 负责管理用户的刷新令牌生命周期，包括：
 * <ul>
 *   <li>保存刷新令牌到 Redis</li>
 *   <li>验证刷新令牌有效性</li>
 *   <li>使单个刷新令牌失效</li>
 *   <li>使用户所有刷新令牌失效（如密码修改时）</li>
 * </ul>
 *
 * <h3>Redis 数据结构设计</h3>
 * <table>
 *   <tr><th>Key 模式</th><th>Value 类型</th><th>说明</th></tr>
 *   <tr><td>refresh_token:{token}</td><td>String</td><td>令牌 -> 用户ID 的映射，用于快速验证和查找</td></tr>
 *   <tr><td>user_refresh_tokens:{userId}</td><td>Set</td><td>用户ID -> 令牌集合 的映射，用于批量失效</td></tr>
 * </table>
 *
 * @see com.example.rag.security.JwtTokenProvider JWT令牌生成器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** Redis 字符串模板 */
    private final StringRedisTemplate redisTemplate;

    /** JWT令牌生成器，用于获取令牌过期时间 */
    private final JwtTokenProvider jwtTokenProvider;

    /** 刷新令牌 Key 前缀 */
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    /** 用户刷新令牌集合 Key 前缀 */
    private static final String USER_REFRESH_TOKENS_PREFIX = "user_refresh_tokens:";

    /**
     * 保存刷新令牌
     * <p>
     * 在 Redis 中存储两个键值对：
     * <ol>
     *   <li>refresh_token:{token} -> userId（用于快速验证）</li>
     *   <li>user_refresh_tokens:{userId} -> {token1, token2, ...}（用于批量失效）</li>
     * </ol>
     *
     * @param userId 用户ID
     * @param refreshToken 刷新令牌字符串
     */
    public void saveRefreshToken(String userId, String refreshToken) {
        // 获取刷新令牌有效期（从 JWT 配置中读取）
        long expiration = jwtTokenProvider.getRefreshExpiration();

        // Key: refresh_token:{token}, Value: userId
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        // Key: user_refresh_tokens:{userId}, Value: Set<token>
        String userTokensKey = USER_REFRESH_TOKENS_PREFIX + userId;

        // 存储令牌到 Redis，设置过期时间
        redisTemplate.opsForValue().set(tokenKey, userId, expiration, TimeUnit.MILLISECONDS);
        // 将令牌添加到用户的令牌集合中
        redisTemplate.opsForSet().add(userTokensKey, refreshToken);
        // 设置集合过期时间
        redisTemplate.expire(userTokensKey, expiration, TimeUnit.MILLISECONDS);

        log.debug("Refresh token saved for user: {}", userId);
    }

    /**
     * 验证刷新令牌是否有效
     *
     * @param refreshToken 刷新令牌字符串
     * @return true 如果令牌存在且未过期，false 否则
     */
    public boolean validateRefreshToken(String refreshToken) {
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey));
    }

    /**
     * 从刷新令牌获取用户ID
     *
     * @param refreshToken 刷新令牌字符串
     * @return 用户ID，如果令牌不存在则返回 null
     */
    public String getUserIdFromRefreshToken(String refreshToken) {
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        return redisTemplate.opsForValue().get(tokenKey);
    }

    /**
     * 使单个刷新令牌失效（退出登录）
     * <p>
     * 同时删除：
     * <ol>
     *   <li>refresh_token:{token} 键</li>
     *   <li>从 user_refresh_tokens:{userId} 集合中移除该令牌</li>
     * </ol>
     *
     * @param refreshToken 要失效的刷新令牌
     */
    public void invalidateRefreshToken(String refreshToken) {
        // 获取令牌对应的用户ID
        String userId = getUserIdFromRefreshToken(refreshToken);

        // 删除令牌 -> 用户ID 的映射
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        redisTemplate.delete(tokenKey);

        // 从用户令牌集合中移除该令牌
        if (userId != null) {
            String userTokensKey = USER_REFRESH_TOKENS_PREFIX + userId;
            redisTemplate.opsForSet().remove(userTokensKey, refreshToken);
        }

        log.debug("Refresh token invalidated");
    }

    /**
     * 使用户所有刷新令牌失效（密码修改时使用）
     * <p>
     * 场景：用户修改密码后，需要使所有设备上的登录会话失效。
     *
     * @param userId 用户ID
     */
    public void invalidateAllRefreshTokens(String userId) {
        String userTokensKey = USER_REFRESH_TOKENS_PREFIX + userId;

        // 获取用户的所有刷新令牌
        var refreshTokens = redisTemplate.opsForSet().members(userTokensKey);
        if (refreshTokens != null) {
            // 遍历删除每个令牌 -> 用户ID 的映射
            for (String refreshToken : refreshTokens) {
                String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
                redisTemplate.delete(tokenKey);
            }
            // 删除用户令牌集合
            redisTemplate.delete(userTokensKey);
        }

        log.debug("All refresh tokens invalidated for user: {}", userId);
    }
}
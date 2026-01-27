package com.docweave.server.auth.service;

import com.docweave.server.common.constant.TokenConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenStorageService {

    private final RedisTemplate<String, String> redisTemplate;

    public void saveRefreshToken(Long userId, String refreshToken, long ttlSeconds) {
        String key = TokenConstant.REFRESH_TOKEN_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);
    }

    public String getRefreshToken(Long userId) {
        String key = TokenConstant.REFRESH_TOKEN_PREFIX + userId;
        return redisTemplate.opsForValue().get(key);
    }

    public void deleteRefreshToken(Long userId) {
        String key = TokenConstant.REFRESH_TOKEN_PREFIX + userId;
        redisTemplate.delete(key);
    }

    public void blacklistToken(String token, long ttlSeconds) {
        String key = TokenConstant.BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "blacklisted", ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isTokenBlacklisted(String token) {
        String key = TokenConstant.BLACKLIST_PREFIX + token;
        return redisTemplate.hasKey(key);
    }
}

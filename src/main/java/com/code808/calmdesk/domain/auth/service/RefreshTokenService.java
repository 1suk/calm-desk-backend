package com.code808.calmdesk.domain.auth.service;

import com.code808.calmdesk.global.config.properties.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenProperties properties;
    private static final String VERSION_PREFIX = "token:version:";

    public void save(String email, String refreshToken) {
        String key = properties.getPrefix() + email;
        redisTemplate.opsForValue().set(
                key,
                refreshToken,
                properties.getTtl(),
                TimeUnit.SECONDS
        );
    }

    public String get(String email) {
        String key = properties.getPrefix() + email;
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String email) {
        String key = properties.getPrefix() + email;
        redisTemplate.delete(key);
    }

    public boolean exists(String email) {
        String key = properties.getPrefix() + email;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean validate(String email, String refreshToken) {
        String storedToken = get(email);
        return storedToken != null && storedToken.equals(refreshToken);
    }

    public long incrementTokenVersion(String email){
        String key = VERSION_PREFIX + email;
        Long version = redisTemplate.opsForValue().increment(key);
        if(version == null) version = 1L;
        redisTemplate.expire(key, properties.getTtl(), TimeUnit.SECONDS);
        return version;
    }

    public long getTokenVersion(String email){
        String key = VERSION_PREFIX + email;
        String value = redisTemplate.opsForValue().get(key);
        if(value == null) return 0L;
        try{
            return Long.parseLong(value);
        }catch(NumberFormatException e){
            return 0L;
        }
    }

    private void deleteTokenVersion(String email) {
        redisTemplate.delete(VERSION_PREFIX + email);
    }
}

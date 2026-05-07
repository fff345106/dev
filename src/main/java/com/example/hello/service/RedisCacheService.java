package com.example.hello.service;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 手动 Redis 缓存服务。
 * 使用 ObjectMapper + activateDefaultTyping(NON_FINAL) 进行 JSON 序列化/反序列化。
 * 不依赖 GenericJackson2JsonRedisSerializer 和 @Cacheable，彻底避免类型解析器冲突。
 */
@Service
public class RedisCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
    }

    /**
     * 从缓存获取对象
     * @param key 缓存键
     * @param clazz 目标类型
     * @return 缓存的对象，不存在则返回 null
     */
    public <T> T get(@NonNull String key, @NonNull Class<T> clazz) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            // 缓存反序列化失败，删除该键
            redisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 从缓存获取泛型对象
     * @param key 缓存键
     * @param typeRef 类型引用（如 new TypeReference<List<StatsResponse>>() {}）
     * @return 缓存的对象，不存在则返回 null
     */
    public <T> T get(@NonNull String key, @NonNull TypeReference<T> typeRef) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            redisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 写入缓存
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 过期时间
     */
    public void put(@NonNull String key, @NonNull Object value, @NonNull Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json != null) {
                redisTemplate.opsForValue().set(key, json, ttl);
            }
        } catch (JsonProcessingException e) {
            // 序列化失败不写缓存
        }
    }

    /**
     * 删除缓存
     */
    public void evict(@NonNull String key) {
        redisTemplate.delete(key);
    }

    /**
     * 删除匹配模式的缓存键
     */
    public void evictPattern(@NonNull String pattern) {
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

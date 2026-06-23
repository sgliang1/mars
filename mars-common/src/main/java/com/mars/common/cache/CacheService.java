package com.mars.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 通用缓存操作服务
 * 封装 RedisTemplate，提供类型安全的缓存操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== 基础读写 ====================

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("缓存写入失败: key={}, error={}", key, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("缓存读取失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    public Boolean delete(String key) {
        try {
            return redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("缓存删除失败: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public Long delete(Collection<String> keys) {
        try {
            return redisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("缓存批量删除失败: error={}", e.getMessage());
            return 0L;
        }
    }

    public Boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean expire(String key, Duration ttl) {
        try {
            return redisTemplate.expire(key, ttl);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 计数器（原子操作） ====================

    public Long increment(String key) {
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("缓存自增失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.warn("缓存自增失败: key={}, delta={}, error={}", key, delta, e.getMessage());
            return null;
        }
    }

    public Long decrement(String key) {
        try {
            return redisTemplate.opsForValue().decrement(key);
        } catch (Exception e) {
            log.warn("缓存自减失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    // ==================== Set 操作 ====================

    public Boolean setAdd(String key, Object value) {
        try {
            Long added = redisTemplate.opsForSet().add(key, value);
            return added != null && added > 0;
        } catch (Exception e) {
            log.warn("Set 添加失败: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public Boolean setRemove(String key, Object value) {
        try {
            Long removed = redisTemplate.opsForSet().remove(key, value);
            return removed != null && removed > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean setIsMember(String key, Object value) {
        try {
            return redisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Set<T> setMembers(String key) {
        try {
            return (Set<T>) redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.warn("Set 读取全部成员失败: key={}, error={}", key, e.getMessage());
            return Collections.emptySet();
        }
    }

    // ==================== 分布式锁 ====================

    public Boolean tryLock(String key, Duration ttl) {
        try {
            return redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Redis 访问器（供调度器等使用） ====================

    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    // ==================== 空值防护（缓存穿透） ====================

    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    /**
     * 写入空值占位符，防止缓存穿透
     */
    public void setNull(String key) {
        set(key, NULL_PLACEHOLDER, NULL_TTL);
    }

    /**
     * 检查是否为空值占位符
     */
    public boolean isNullPlaceholder(Object value) {
        return NULL_PLACEHOLDER.equals(value);
    }
}
package com.interstellar.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
    private final StringRedisTemplate stringRedisTemplate;

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

    // ==================== SCAN 安全扫描 ====================

    /**
     * 使用 SCAN 替代 KEYS，避免阻塞 Redis 主线程
     * KEYS 是 O(N) 阻塞操作，数据量大时会冻结 Redis；SCAN 通过游标分批扫描
     */
    public Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try {
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                try (var cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next()));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("SCAN 操作失败: pattern={}, error={}", pattern, e.getMessage());
        }
        return keys;
    }

    // ==================== 分布式锁 ====================

    /**
     * Lua 脚本：原子性校验并删除锁（仅当 value 匹配时才删除，防止误删其他线程的锁）
     * KEYS[1] = lock key，ARGV[1] = expected owner token
     * 返回 1 = 成功删除，0 = 不是自己的锁或锁不存在
     */
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end",
            Long.class
    );

    /**
     * 尝试获取分布式锁
     * @param key  锁 key
     * @param ttl  锁过期时间
     * @return 锁的 owner token（UUID），获取失败返回 null
     */
    public String tryLock(String key, Duration ttl) {
        try {
            String token = UUID.randomUUID().toString();
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 释放分布式锁（仅当 owner 匹配时才删除）
     * @param key   锁 key
     * @param owner tryLock 返回的 owner token
     * @return true = 成功释放，false = 非 owner 或锁不存在
     */
    public boolean unlock(String key, String owner) {
        try {
            Long result = stringRedisTemplate.execute(
                    UNLOCK_SCRIPT, Collections.singletonList(key), owner);
            return result != null && result > 0;
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
-- 滑动窗口限流 Lua 脚本
-- 使用 Redis ZSET 实现原子性滑动窗口计数
--
-- KEYS[1] 限流 Key（如 mars:rl:ip:192.168.1.1:w）
-- ARGV[1] 当前时间戳（毫秒）
-- ARGV[2] 窗口大小（毫秒）
-- ARGV[3] 限额
-- ARGV[4] 唯一请求标识
--
-- 返回值：
--   result[1] 当前计数（通过时为新计数，拒绝时为当前计数）
--   result[2] 0（通过）或 retryAfter 秒数（拒绝）

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local uniqueId = ARGV[4]

-- 清除滑动窗口外的旧条目
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 当前窗口内的请求数
local count = redis.call('ZCARD', key)

if count < limit then
    -- 未超限：添加当前请求
    redis.call('ZADD', key, now, uniqueId)
    redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
    return {count + 1, 0}
else
    -- 已超限：计算重试等待时间
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfter = 0
    if #oldest > 0 then
        retryAfter = math.ceil((tonumber(oldest[2]) + window - now) / 1000)
        if retryAfter < 1 then retryAfter = 1 end
    end
    return {count, retryAfter}
end

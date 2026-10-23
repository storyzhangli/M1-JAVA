-- Redis 令牌桶限流脚本（与 Spring Cloud Gateway RequestRateLimiter 同款算法）
-- KEYS[1] = 令牌数键（tokens）
-- KEYS[2] = 上次刷新时间键（timestamp）
-- ARGV[1] = replenishRate 每秒补充令牌数
-- ARGV[2] = burstCapacity 桶容量
-- ARGV[3] = now 当前时间（毫秒）
-- ARGV[4] = requestedTokens 本次请求消耗令牌数
-- 返回：{是否允许(1/0), 剩余令牌数}

local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]

local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

-- 桶被填满所需时间；键的 TTL 取其 2 倍，保证空闲一段时间后状态被清理
local fill_time = capacity / rate
local ttl = math.floor(fill_time * 2)
if ttl < 1 then
    ttl = 1
end

-- 首次访问时桶为满
local last_tokens = tonumber(redis.call("get", tokens_key))
if last_tokens == nil then
    last_tokens = capacity
end

local last_refreshed = tonumber(redis.call("get", timestamp_key))
if last_refreshed == nil then
    last_refreshed = 0
end

-- 按距上次刷新的时间差补充令牌，但不超过桶容量
local delta = math.max(0, now - last_refreshed)
local filled_tokens = math.min(capacity, last_tokens + (delta * rate / 1000.0))

local allowed = filled_tokens >= requested
local new_tokens = filled_tokens
local allowed_num = 0
if allowed then
    new_tokens = filled_tokens - requested
    allowed_num = 1
end

redis.call("setex", tokens_key, ttl, new_tokens)
redis.call("setex", timestamp_key, ttl, now)

return { allowed_num, new_tokens }

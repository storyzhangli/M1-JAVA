-- 幂等记录「所有权校验更新（置为 COMPLETED）」脚本
-- 作用：业务成功后，仅当记录的 ownerToken 与当前请求一致才覆盖结果并续期，
--       避免旧请求覆盖已被新请求重新占位的记录。
-- KEYS[1] = 幂等记录键
-- ARGV[1] = 当前请求的 ownerToken
-- ARGV[2] = 新的记录 JSON（COMPLETED）
-- ARGV[3] = 过期时间（毫秒）
-- 返回：1=更新成功；0=记录不存在或所有权已不属于本请求（放弃覆盖）
local key = KEYS[1]
local owner = ARGV[1]
local new_value = ARGV[2]
local ttl_ms = tonumber(ARGV[3])

local current = redis.call('GET', key)
if not current then
    return 0
end

local ok, obj = pcall(cjson.decode, current)
if not ok or not obj.ownerToken or obj.ownerToken ~= owner then
    return 0
end

redis.call('SET', key, new_value, 'PX', ttl_ms)
return 1

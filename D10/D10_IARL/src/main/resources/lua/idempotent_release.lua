-- 幂等记录「所有权校验删除」脚本
-- 作用：业务失败时，仅当记录的 ownerToken 与当前请求一致才删除，避免误删他人重新占位的新记录。
-- KEYS[1] = 幂等记录键
-- ARGV[1] = 当前请求的 ownerToken
-- 返回：1=删除成功（本请求仍持有所有权）；0=记录不存在或所有权已不属于本请求
local key = KEYS[1]
local owner = ARGV[1]

local current = redis.call('GET', key)
if not current then
    return 0
end

local ok, obj = pcall(cjson.decode, current)
if not ok or not obj.ownerToken or obj.ownerToken ~= owner then
    return 0
end

return redis.call('DEL', key)

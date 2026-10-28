-- 原子保证：写标记前 Hash 一定存在且带 __creating__ 占位/TTL，消除 hasKey→HSET 的过期竞态
-- KEYS[1]=hashKey  ARGV[1]=ttl（秒）
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))   -- 已存在 → 续期（不存在的 key 是 no-op）
if redis.call('EXISTS', KEYS[1]) == 0 then          -- 真不存在 → 补占位
    redis.call('HSET', KEYS[1], '__creating__', '1')
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return 1

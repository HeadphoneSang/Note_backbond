-- 版本 CAS：确保落到缓存的一定是最新版本
-- KEYS[1]=hashKey  ARGV[1]=blockId  ARGV[2]=newValue  ARGV[3]=newVersion
-- ARGV[4]=mode("data"/"dirty")  ARGV[5]=allowAbsent(0/1，仅 data 模式生效)
-- 返回 1=写入成功  0=CAS 拒绝（缓存已有更新版本 / 块已删除）
-- 使用示例：call(RedisKey.NOTEBLOCKs.bluid(...),dirty_blockId,"version:x.x|data",newVer,dirty,0) 更新redis对应的块为脏
local cur = redis.call('HGET', KEYS[1], ARGV[1])
local curVer = 0
local absent = (cur == false)
local newVer = tonumber(ARGV[3])
if not absent then  -- 如果不为空，就说明这个块的缓存存在
    local dv = string.match(cur, '^version:(%d+)$')          -- 脏标记 version:N
    if dv then
        curVer = tonumber(dv)
    else
    -- 如果当前块有完整的数据，说明有线程在此之前回填了，什么情况会触发回填：1. 冷启动，2.缓存过期 3. 查询到脏标记
        curVer = newVer - 1 -- 如果当前块有完整的数据，就删除这个数据，强制重载缓存
    end
end


if ARGV[4] == 'dirty' then
    -- 写脏标记：快存在，新的版本大于旧版本，允许覆盖脏标记
    if (not absent) and (newVer >= curVer) then
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
        return 1
    -- 写脏标记的时候，如果块不存在，一定是被删除了，不可能是新的快，因为新的块不走脏标记，直接通过data插入。
    -- 而且删除肯定是最新操作，如果删除不是最新操作，那么对应的更新版本的更新不可能发生，因为有数据库的乐观锁兜底，所以返回0
    elseif absent then
        return 0
    end
else
    -- 读回填：field 不存在时，仅冷启动全量回填（allowAbsent=1）允许写入；
    -- 完整 Hash 上 field 不存在 = 块已删除 → 拒绝
    local allowAbsent = tonumber(ARGV[5])
    if absent then
        if allowAbsent == 1 then
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            return 1
        end
    elseif newVer >= curVer then
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
        return 1
    end
end
return 0
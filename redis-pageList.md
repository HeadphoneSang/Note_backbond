# Redis 列表分页缓存成熟设计（读多写少）

> 适用场景：业务列表分页查询，读多写少，Redis 承担大部分读压力；**禁止直接用 List + LRANGE 做大偏移分页，大 offset 性能 O(N) 会恶化**

## 方案对比

|方案|适用场景|缺点|
|---|---|---|
|List + LRANGE|小列表，仅访问前几页|offset 大性能暴跌，不支持深分页|
|**ZSet + Hash（生产首选）**|通用分页、排序，读多写少|需要维护两套 key，多一次网络IO|
|预分页缓存（page:N）|几乎不变的静态热点列表|增删后大量页面失效，维护成本高|

## 核心方案：ZSet + Hash

> ZSet 只维护 ID 索引，Hash 存储完整业务实体

### Key 设计

```redis
# ZSet：列表索引，score=排序值（时间戳/热度/权重），member=业务ID
cache:article:list:hot

# Hash：单条业务实体
cache:article:item:{id}
```

### 分页查询逻辑

参数：`pageNo`、`pageSize`

```text
start = (pageNo - 1) * pageSize
end = start + pageSize - 1

1. zrevrange key start end → 获取一页ID列表  O(logM + N)，性能与offset无关
2. pipeline 批量 hgetall 根据ID拿Hash实体数据
3. zcard 获取列表总条数
```

### CRUD 操作

|操作|Redis操作|说明|
|---|---|---|
|新增|zadd + hmset（需不需要原子）|写入索引与实体|
|修改内容|仅更新 hash (标记为脏还是直接删除，删除还有zset兜底)|不改动 ZSet 索引|
|修改排序权重|zadd 更新 score|调整列表顺序|
|删除|zrem + del hash（需不需要原子）|移除索引，删除实体|

### 缓存更新策略
- 模式：**Cache Aside 旁路缓存**
- 流程：更新 DB → 更新 Redis；读请求优先走 Redis，缓存未命中回源 DB 回填
- 过期兜底：ZSet、Hash 均设置 TTL（30min~2h），防止永久脏数据
## 生产环境关键细节

1. **大 Key 风险**
   - ZSet 避免无限膨胀，必要时 `zremrangebyrank` 裁剪淘汰旧数据
   - Hash 不存放超大文本，大字段单独 String 存储
2. **一致性**
   - 读多写少场景优先**写库后更新缓存**；写 Redis 失败靠 TTL 兜底
   - 写并发高场景改为写库后删除缓存
3. **过滤限制**
   - ZSet 不支持条件过滤；过滤条件少维护多套 ZSet；过滤条件多交给 ES
4. **深度分页**
   - 业务层限制最大页码，禁止无限制深翻页
5. **缓存穿透 / 击穿**
   - zcard=0 区分真实空列表 & 缓存未初始化；增加空缓存保护
6. **网络优化**
   - 批量获取实体必须使用 pipeline，减少多次 RTT

## 不适合 Redis 做列表缓存的场景

1. 多条件复杂筛选、多维度过滤 → 使用 ES
2. 极高频率增删改，索引维护开销大
3. 数据量级过大，产生海量 key

## 简短总结

> 
> 读多写少分页列表标准实现：**ZSet 存 ID 索引做分页排序，Hash 存业务实体**；修改内容仅更新 Hash；TTL 兜底脏数据；业务层限制最大页码；不要使用原生 List 做深分页。
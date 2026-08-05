# Redis 缓存改造方案

> 基于 `note_backbond` 项目架构，按企业级生产标准设计。

---

## 目录

1. [现状分析](#1-现状分析)
2. [缓存架构设计](#2-缓存架构设计)
3. [缓存场景详细设计](#3-缓存场景详细设计)
4. [企业级防崩溃策略](#4-企业级防崩溃策略)
5. [逐出与过期策略](#5-逐出与过期策略)
6. [监控与可观测性](#6-监控与可观测性)
7. [实施优先级](#7-实施优先级)

---

## 1. 现状分析

### 1.1 现有 Redis 使用情况

当前 Redis 仅用于**临时性、高时效**场景，全部是"用完即焚"的数据：

| 用途 | Key 模式 | TTL | 特点 |
|------|----------|-----|------|
| 图形验证码 | `captcha:<uuid>` | 5min | 一次性，读后删 |
| 邮箱验证码 | `email:code:<email>` | 5min | 一次性，读后删 |
| 发送冷却 | `email:code:<email>:ttl` | 60s | 防刷 |
| 登录失败计数 | `login:fail:account:<account>` | 30min | 自增原子操作 |
| 账号锁定 | `login:lock:account:<account>` | 15min | 5次失败后锁定 |
| Token 注销 | `logout:token` | 7天 | 黑名单校验 |

### 1.2 缺少的缓存能力

- **业务数据缓存**：笔记内容、笔记列表、笔记本列表等高频读取数据无缓存
- **本地缓存**：缺少 Caffeine 一级缓存，每次请求都穿透到 Redis 或 DB
- **分布式锁**：NoteBlock 并发更新依赖乐观锁重试，缺少分布式互斥能力
- **接口限流**：全部接口无速率限制，极端流量下无保护
- **缓存预热**：启动时冷缓存，首个用户请求必然击穿到 DB

---

## 2. 缓存架构设计

### 2.1 多级缓存架构（Caffeine + Redis 二级缓存）

```
┌──────────────┐
│   客户端请求   │
└──────┬───────┘
       │
┌──────▼───────┐    命中    ┌──────────────┐
│  Caffeine    │ ────────→  │   直接返回    │
│  (本地缓存)   │            └──────────────┘
│  L1: 10~50ms │
│  容量: 千级   │
└──────┬───────┘
       │ 未命中
┌──────▼───────┐    命中    ┌──────────────┐
│    Redis     │ ────────→  │  回写 L1 +    │
│  (分布式缓存) │            │   返回结果     │
│  L2: 1~5ms   │            └──────────────┘
│  容量: GB级   │
└──────┬───────┘
       │ 未命中
┌──────▼───────┐
│   MySQL DB   │ ────────→  回写 L1 + L2 + 返回
│   L3: 10~50ms│
└──────────────┘
```

### 2.2 依赖引入（pom.xml）

```xml
<!-- Caffeine 本地缓存 -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Redisson 分布式锁 + 高级数据结构 -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.22.1</version>
</dependency>

<!-- Spring Cache 抽象（可选，看团队偏好） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<!-- Micrometer + Prometheus 监控 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 2.3 全局 Key 命名规范

```
note_backbond:<业务域>:<实体类型>:<标识符>[:<子标识符>]

示例：
note_backbond:note:content:123          → 笔记内容
note_backbond:note:list:user:456:page:1 → 用户笔记列表（分页）
note_backbond:notebook:list:user:456    → 用户笔记本列表
note_backbond:lock:note:123             → 笔记分布式锁
note_backbond:rate:limit:ip:192.168.*   → IP 限流
```

---

## 3. 缓存场景详细设计

### 3.1 笔记内容缓存（最高优先级）

**场景**：`GET /noteBlock/getBlocks`，读取笔记的所有 NoteBlock，是编辑器最核心的读取接口。

**问题**：每次打开笔记都查询 `note_block` 表，同一个笔记被频繁打开，DB 重复查询。

**设计方案**：

```java
// CacheAside 模式：读时回写，写时失效
@Service
public class NoteBlockCacheService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheManager caffeineCacheManager; // Caffeine CacheManager

    // 构建缓存 key
    private String buildBlockCacheKey(Integer noteId) {
        return "note_backbond:note:content:" + noteId;
    }

    // 读：多级缓存读取
    public List<NoteBlock> getNoteBlocks(Integer noteId) {
        String cacheKey = buildBlockCacheKey(noteId);

        // 1. 尝试 Caffeine（本地缓存）
        Cache<Object, Object> localCache = caffeineCacheManager.getCache("noteBlocks");
        if (localCache != null) {
            ValueWrapper wrapper = localCache.get(cacheKey);
            if (wrapper != null) {
                return (List<NoteBlock>) wrapper.get();
            }
        }

        // 2. 尝试 Redis（分布式缓存）
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(json)) {
            List<NoteBlock> blocks = JSON.parseArray(json, NoteBlock.class);
            // 回写本地缓存
            localCache.put(cacheKey, blocks);
            return blocks;
        }

        // 3. 回源 DB（互斥锁防击穿）
        List<NoteBlock> blocks = noteBlockService.selectNoteBlocks(noteId);
        if (blocks != null) {
            // 回写两级缓存
            String jsonStr = JSON.toJSONString(blocks);
            redisTemplate.opsForValue().set(cacheKey, jsonStr, 30, TimeUnit.MINUTES);
            localCache.put(cacheKey, blocks);
        }
        return blocks;
    }

    // 写：更新后立即失效缓存
    public void evictNoteBlockCache(Integer noteId) {
        String cacheKey = buildBlockCacheKey(noteId);
        // 1. 先删除 Redis（作为分布式锁的协调者）
        redisTemplate.delete(cacheKey);
        // 2. 清除本地缓存（所有实例都需要清除，但无法广播，靠短过期时间兜底）
        Cache<Object, Object> localCache = caffeineCacheManager.getCache("noteBlocks");
        if (localCache != null) {
            localCache.evict(cacheKey);
        }
    }
}
```

**缓存粒度**：按 `noteId` 缓存整个笔记的 NoteBlock 列表。

**TTL**：30 分钟（热点笔记会自动续期）。

**数据量估算**：一条笔记平均 20 个 block，每个 block ~500B，约 10KB/条。10 万条活跃笔记 ≈ 1GB Redis 内存。

---

### 3.2 笔记列表缓存（高优先级）

**场景**：`GET /note/list?notebookId=xx&page=1&size=20` 和 `GET /note/notes?page=1&size=20`。

**问题**：分页查询涉及 LEFT JOIN + 标签子查询，是项目中开销最大的查询。用户频繁切换笔记本、刷新列表。

**设计方案**：

```java
@Service
public class NoteListCacheService {

    // 缓存笔记列表（不含标签，标签单独缓存）
    public PageResult<Note> getNotePage(Integer notebookId, Integer userId, long current, long size) {
        String cacheKey = buildPageCacheKey(notebookId, userId, current, size);

        // Redis 缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, PageResult.class);
        }

        // 互斥锁：只让一个线程回源 DB
        String lockKey = "note_backbond:lock:page:" + notebookId + ":" + userId;
        boolean locked = redisLock.tryLock(lockKey, 3, TimeUnit.SECONDS);
        if (!locked) {
            // 没拿到锁，短暂等待后重试读缓存
            ThreadUtil.sleep(50);
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return JSON.parseObject(cached, PageResult.class);
            }
        }

        try {
            // 这里有个设计点：缓存第一页即可，因为 80% 的用户只看第一页
            // 其他页按需缓存，TTL 缩短一半
            PageResult<Note> result = noteService.getNotesByNotebookId0(notebookId, current, size);
            if (result != null) {
                long ttl = current == 1 ? 5 : 2; // 首页 5min，翻页 2min
                redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), ttl, TimeUnit.MINUTES);
            }
            return result;
        } finally {
            if (locked) {
                redisLock.unlock(lockKey);
            }
        }
    }
}
```

**TTL**：首页 5 分钟，翻页 2 分钟（翻页数据变化容忍度高）。

**失效时机**：笔记创建、删除、移动笔记本时，清除对应笔记本的列表缓存。

---

### 3.3 笔记本列表缓存（中优先级）

**场景**：`GET /notebook/list` 和 `GET /notebook/getNotebookList`，每次进入笔记首页都加载。

**设计方案**：

```java
// 缓存整个用户的笔记本列表
// 数据量小：一个用户通常不超过 50 个笔记本
// 变更频率低：创建/删除笔记本时才需要失效

public List<Notebook> getNotebookList(Integer userId) {
    String cacheKey = "note_backbond:notebook:list:user:" + userId;

    return redisTemplate.opsForValue()
        .getAndExpire(cacheKey, 10, TimeUnit.MINUTES) // 访问时自动续期
        .orElseGet(() -> {
            List<Notebook> list = notebookService.getNotebooksByUserId(userId);
            redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(list), 10, TimeUnit.MINUTES);
            return list;
        });
}
```

**TTL**：10 分钟。

**失效时机**：新建笔记本、删除笔记本时清除。

---

### 3.4 用户信息缓存（中优先级）

**场景**：`GET /user/info`，每次请求都查 DB。

**现状**：JWT 拦截器每次请求都解析 token 获取 userId，但用户信息没有缓存。

**设计方案**：

```java
// 用户信息 30 分钟缓存
// 注意：用户信息变更频率极低，但读取频率高（每次请求都可能用来校验权限）
public User getUserInfo(Integer userId) {
    String cacheKey = "note_backbond:user:info:" + userId;
    // 使用 Caffeine 本地缓存（用户信息的读多写少特性非常适合本地缓存）
    return caffeineCache.get(cacheKey, key -> {
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            return JSON.parseObject(json, User.class);
        }
        User user = userService.getUserInfo(userId);
        if (user != null) {
            redisTemplate.opsForValue().set(key, JSON.toJSONString(user), 30, TimeUnit.MINUTES);
        }
        return user;
    });
}
```

**TTL**：30 分钟（本地缓存 5 分钟）。

**失效时机**：更新用户信息、修改密码后立即清除。

---

### 3.5 标签缓存（中优先级）

**场景**：`GET /tag/list`，标签列表查询，每页笔记列表都要加载标签。

**现状**：`extractTags4Notes` 每次查笔记列表都批量查询标签映射。

**设计方案**：

```java
// 标签数据量极小（一个用户通常 10~50 个标签），适合全量缓存
// 变更频率低，读取频率高

public List<Tag> getUserTags(Integer userId) {
    String cacheKey = "note_backbond:tag:user:" + userId;
    // 使用 Redis Hash 结构，可以单独增删改单个标签
    Map<Object, Object> tagMap = redisTemplate.opsForHash().entries(cacheKey);
    if (CollUtil.isNotEmpty(tagMap)) {
        return tagMap.values().stream()
                .map(v -> JSON.parseObject((String) v, Tag.class))
                .collect(Collectors.toList());
    }
    // 回源 DB
    List<Tag> tags = tagService.list(userId);
    if (CollUtil.isNotEmpty(tags)) {
        Map<String, String> entries = tags.stream()
                .collect(Collectors.toMap(t -> t.getId().toString(), JSON::toJSONString));
        redisTemplate.opsForHash().putAll(cacheKey, entries);
        redisTemplate.expire(cacheKey, 30, TimeUnit.MINUTES);
    }
    return tags;
}
```

**TTL**：30 分钟。

**失效时机**：创建标签、删除标签时清除。

---

### 3.6 笔记操作分布式锁（高优先级）

**场景**：`POST /noteBlock/batchUpdate` 并发更新同一个笔记的 NoteBlock。

**问题**：当前的乐观锁机制在同一条笔记被多人同时编辑时，失败率很高。乐观锁 + 重试可以解决一部分问题，但大量冲突会导致"惊群效应"——大量请求同时失败重试，DB 压力飙升。

**设计方案**：

```java
@Service
public class NoteBlockBatchService {

    @Autowired
    private RedissonClient redissonClient;

    public NoteDiffResult updateNoteBlocksDiff(NoteDiffRequest request) {
        Integer noteId = extractNoteId(request);
        String lockKey = "note_backbond:lock:note:" + noteId;
        RLock lock = redissonClient.getLock(lockKey);

        // 等待 3 秒，持有锁 10 秒（单个用户的操作通常 < 1 秒）
        boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
        if (!acquired) {
            throw new ServiceException("笔记正在被其他用户编辑，请稍后重试");
        }

        try {
            // 执行批量差异更新
            return doUpdateNoteBlocksDiff(request);
        } finally {
            // 释放锁 + 清除缓存
            lock.unlock();
            noteBlockCacheService.evictNoteBlockCache(noteId);
        }
    }
}
```

**为什么不用数据库乐观锁就行**：乐观锁假设冲突少，但在多人协作编辑同一个笔记时，冲突概率高。大量冲突导致：
1. 客户端频繁重试，用户体验差
2. 重试请求堆积，DB 连接池被打满
3. 无公平性保障，可能某个请求永远抢不到

**分布式锁 + 阻塞等待**比乐观锁+重试更适合这种场景。

---

### 3.7 接口限流（高优先级）

**设计方案**：

```java
// 注解 + AOP 实现
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key();              // 限流 key
    int maxCount();            // 最大请求数
    int timeWindow();          // 时间窗口（秒）
    TimeUnit unit() default TimeUnit.SECONDS;
}

// 切面实现
@Around("@annotation(rateLimit)")
public Object rateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
    String key = "note_backbond:rate:limit:" + rateLimit.key();
    // 使用滑动窗口算法（Redis Sorted Set）
    long now = System.currentTimeMillis();
    long windowStart = now - rateLimit.unit().toMillis(rateLimit.timeWindow());

    // 移除窗口外的数据
    redisTemplate.opsForZSet().removeByScore(key, 0, windowStart);
    Long count = redisTemplate.opsForZSet().zCard(key);

    if (count != null && count >= rateLimit.maxCount()) {
        throw new RateLimitException("请求过于频繁，请稍后重试");
    }

    redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
    redisTemplate.expire(key, rateLimit.timeWindow(), rateLimit.unit());
    return pjp.proceed();
}
```

**限流粒度**：

| 粒度 | 接口 | 阈值 | 说明 |
|------|------|------|------|
| 用户 | 全部写接口 | 30次/分钟 | 防止单个用户刷接口 |
| 用户 | 笔记列表 | 60次/分钟 | 防止频繁翻页 |
| IP | 验证码 | 10次/分钟 | 防刷 |
| IP | 登录 | 5次/分钟 | 防暴力破解 |
| 全局 | 全站 | 1000次/秒 | 突发流量兜底 |

---

### 3.8 缓存预热（低优先级）

**问题**：应用重启后缓存为空，第一个用户请求所有数据都穿透到 DB，响应慢且 DB 压力大。

**设计方案**：

```java
@Component
public class CacheWarmupRunner implements CommandLineRunner {

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        // 预热策略：不预热全部，只预热"确定性热点"
        // 1. 系统配置（无）
        // 2. 热门用户数据（需依赖已有访问统计，首版不做）

        log.info("缓存预热完成（当前版本无需预热静态数据）");
    }
}
```

**首版不做大规模预热**，因为笔记应用的用户数据天然隔离，没有全局共享的"热门数据"。预热反而会导致启动时间变长。

---

## 4. 企业级防崩溃策略

### 4.1 缓存穿透

**场景**：请求一个不存在的笔记 ID，每次都穿透到 DB。

**解决方案**：**布隆过滤器 + 空值缓存**

```java
// 方式一：布隆过滤器（防止大量不存在的 ID 请求打到 DB）
@Component
public class NoteBloomFilter {
    private final BloomFilter<Integer> bloomFilter = BloomFilter.create(
            Funnels.integerFunnel(), 100_000, 0.01);

    @PostConstruct
    public void init() {
        // 从 DB 加载所有存在的 noteId
        List<Integer> allIds = noteMapper.selectAllIds();
        allIds.forEach(bloomFilter::put);
    }

    public boolean mightExist(Integer noteId) {
        return bloomFilter.mightContain(noteId);
    }
}

// 方式二：空值缓存（简单有效，推荐首版使用）
// 查询 DB 返回 null 时，缓存一个空值标记，TTL 设短（30~60 秒）
if (result == null) {
    redisTemplate.opsForValue().set(cacheKey, "NULL", 30, TimeUnit.SECONDS);
    return null;
}
```

### 4.2 缓存击穿

**场景**：热点笔记的缓存过期，大量请求同时打到 DB。

**解决方案**：**互斥锁**（已在 3.2 中实现）或 **热点 key 永不过期 + 后台异步刷新**

```java
// 方案：热点 key 永不过期 + 后台线程异步刷新
// 适用于：用户反复打开的笔记（如首页笔记、置顶笔记）
@Component
public class HotKeyRefresher {

    @PostConstruct
    public void startRefreshTask() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // 每 5 分钟扫描并刷新热点 key
        scheduler.scheduleAtFixedRate(() -> {
            // 1. 从 Redis 获取热点 key 列表（可通过访问计数统计）
            Set<String> hotKeys = redisTemplate.opsForSet().members("hot:keys");
            for (String key : hotKeys) {
                // 2. 检查是否接近过期（TTL < 2min）
                Long ttl = redisTemplate.getExpire(key);
                if (ttl != null && ttl < 120) {
                    // 3. 异步刷新缓存
                    refreshCache(key);
                }
            }
        }, 1, 5, TimeUnit.MINUTES);
    }
}
```

### 4.3 缓存雪崩

**场景**：大量缓存同时过期，或 Redis 宕机，所有请求打到 DB。

**解决方案**：

```java
// 1. TTL 添加随机偏移，防止集体过期
long ttl = baseTtl + ThreadLocalRandom.current().nextLong(0, 300); // 基础 TTL + 0~5min 随机

// 2. 本地缓存兜底（Caffeine 作为 Redis 宕机时的最后防线）
// 即使 Redis 挂了，Caffeine 中的缓存还能扛住一部分流量

// 3. 限流降级（Redis 宕机时，DB 保护比功能完整更重要）
@Component
public class DBCircuitBreaker {
    private final AtomicInteger concurrentDbQueries = new AtomicInteger(0);
    private static final int MAX_DB_CONCURRENT = 50; // 最大并发 DB 查询数

    public <T> T executeWithDbFallback(Supplier<T> dbSupplier) {
        if (concurrentDbQueries.incrementAndGet() > MAX_DB_CONCURRENT) {
            concurrentDbQueries.decrementAndGet();
            throw new ServiceBusyException("系统繁忙，请稍后重试");
        }
        try {
            return dbSupplier.get();
        } finally {
            concurrentDbQueries.decrementAndGet();
        }
    }
}
```

### 4.4 缓存一致性保障

**原则"Cache Aside Pattern"**：**更新 DB 后，删除缓存，而不是更新缓存。**

```
为什么删缓存而不是更新缓存？
1. 更新缓存存在并发问题：线程A写DB，线程B写DB，线程B写缓存，线程A写缓存 → 脏数据
2. 删除缓存更简单，下次读取时自动回写
3. 删除是幂等操作，多次删除无副作用
```

**最终一致性方案（延迟双删）**：

```java
@Transactional
public void updateNote(Note note) {
    // 1. 先删除缓存
    redisTemplate.delete(cacheKey);

    // 2. 更新 DB
    noteMapper.updateById(note);

    // 3. 延迟 N 毫秒后再删一次（解决并发读回写脏数据问题）
    // 使用 MQ 或线程池异步执行
    delayedDeleteCache(cacheKey, 500);
}
```

**对于本项目**：当前架构是单服务部署，并发压力不大，**Cache Aside 的简单删除策略已足够**。延迟双删在后续多实例部署时再引入。

---

## 5. 逐出与过期策略

### 5.1 Redis 逐出策略

```
maxmemory-policy allkeys-lru
```

**理由**：笔记数据天然具有"热点"属性（最近编辑的笔记最常被读取），LRU 策略最适合。

### 5.2 Caffeine 本地缓存配置

```java
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)                 // 最大 1000 条
                .expireAfterWrite(5, TimeUnit.MINUTES)  // 写入后 5 分钟过期
                .recordStats()                      // 记录命中率
        );
        manager.setCacheNames(Arrays.asList(
                "noteBlocks", "noteList", "userInfo", "notebookList"
        ));
        return manager;
    }
}
```

### 5.3 各场景缓存参数汇总

| 缓存场景 | Redis TTL | Caffeine TTL | 最大容量 | 失效时机 |
|----------|-----------|-------------|---------|---------|
| 笔记内容 | 30min + 随机偏移 | 5min | 1000 条 | 笔记内容更新后 |
| 笔记列表 | 首页5min/翻页2min | 3min | 500 条 | 创建/删除/移动笔记 |
| 笔记本列表 | 10min | 5min | 200 条 | 创建/删除笔记本 |
| 用户信息 | 30min | 5min | 500 条 | 更新用户信息 |
| 标签列表 | 30min | 10min | 200 条 | 创建/删除标签 |
| 空值标记 | 30~60s | 无 | 100 条 | 自动过期 |

---

## 6. 监控与可观测性

### 6.1 缓存命中率监控

```java
@Component
public class CacheMetrics {

    private final MeterRegistry meterRegistry;

    // 记录缓存命中/未命中
    public void recordHit(String cacheName) {
        meterRegistry.counter("cache.hit", "cache", cacheName).increment();
    }

    public void recordMiss(String cacheName) {
        meterRegistry.counter("cache.miss", "cache", cacheName).increment();
    }

    // 记录缓存大小
    public void recordSize(String cacheName, long size) {
        meterRegistry.gauge("cache.size", Tags.of("cache", cacheName), size);
    }
}
```

### 6.2 需要关注的指标

| 指标 | 告警阈值 | 含义 |
|------|---------|------|
| `cache.hit.ratio` | < 80% | 缓存命中率过低，检查 TTL 设置 |
| `cache.miss.count` | 突增 200% | 可能发生了缓存击穿/雪崩 |
| `redis.memory.used` | > 80% | Redis 内存不足，检查 LRU 是否生效 |
| `redis.commands.failed` | > 0 | Redis 连接异常，触发降级 |
| `db.connections.active` | > 80% | DB 压力过大，缓存可能已失效 |

---

## 7. 实施优先级

| 优先级 | 场景 | 工作量 | 收益 | 说明 |
|--------|------|--------|------|------|
| P0 | 接口限流 | 小 | 保障系统稳定性 | 必须先做，防止被刷 |
| P0 | 分布式锁 | 中 | 解决并发编辑冲突 | 核心用户体验 |
| P1 | 笔记内容缓存 | 中 | 降低 NoteBlock 查询 90% | 最高频读取接口 |
| P1 | 笔记列表缓存 | 中 | 降低 LEFT JOIN 查询 80% | 最重查询 |
| P2 | 笔记本列表缓存 | 小 | 降低查询 70% | 数据量小，实现简单 |
| P2 | 用户信息缓存 | 小 | 降低查询 90% | 变更少，读取多 |
| P2 | 标签缓存 | 小 | 降低查询 80% | 数据量小 |
| P3 | 防击穿/雪崩 | 中 | 保障极端场景稳定 | 流量达到一定规模后再做 |
| P3 | 缓存预热 | 小 | 提升首次请求体验 | 用户感知不明显 |
| P3 | 监控告警 | 大 | 可观测性 | 基础设施，长期受益 |

### 推荐实施步骤

**第一阶段（P0）**：限流 + 分布式锁 → 2 天
**第二阶段（P1）**：笔记内容缓存 + 笔记列表缓存 → 3 天
**第三阶段（P2）**：笔记本列表 + 用户信息 + 标签缓存 → 2 天
**第四阶段（P3）**：防崩溃加固 + 监控告警 → 持续迭代
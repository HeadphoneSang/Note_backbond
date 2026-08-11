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
- **接口限流**：全部接口无速率限制，极端流量下无保护
- **缓存预热**：启动时冷缓存，首个用户请求必然击穿到 DB

---

## 2. 缓存架构设计

### 2.1 两层缓存架构（Redis + DB）

```
┌──────────────┐
│   客户端请求   │
└──────┬───────┘
       │
┌──────▼───────┐    命中    ┌──────────────┐
│    Redis     │ ────────→  │   直接返回     │
│  (分布式缓存) │            └──────────────┘
│  L1: 1~5ms   │
│  容量: GB级   │
└──────┬───────┘
       │ 未命中
┌──────▼───────┐
│   MySQL DB   │ ────────→  回写 Redis + 返回
│   L2: 10~50ms│
└──────────────┘
```

**为什么不引入 Caffeine 本地缓存（移除理由）**：

1. **本项目是单实例部署**，Redis 本身延迟 ~1ms，本地缓存带来的边际收益极小；
2. 多级缓存引入"各实例缓存不一致 + 失效无法广播"问题，复杂度远大于收益；
3. Redis 宕机时用**限流 + DB 熔断**保护即可（见 4.3），不需要本地缓存兜底。

结论：只保留 Redis 一层缓存，简单可靠。

### 2.2 依赖引入（pom.xml）

```xml
<!-- Redis 连接池（已存在） -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

> **注意：pom.xml 已移除 Caffeine、Redisson、Micrometer 等非必需依赖，只保留 Redis 相关。** 缓存读写统一用 `StringRedisTemplate` + JSON 序列化；互斥锁用简单 `SET NX EX` 实现（见 3.2），不引入 Redisson。

### 2.3 全局 Key 命名规范

```
note_backbond:<业务域>:<实体类型>:<标识符>[:<子标识符>]

示例：
note_backbond:note:blocks:<noteId>       → 笔记全部块（Hash，field=块ID）
note_backbond:note:list:user:456:page:1  → 用户笔记列表（分页）
note_backbond:notebook:list:user:456     → 用户笔记本列表
note_backbond:rate:limit:ip:192.168.*    → IP 限流
```

### 2.4 Key 与 Lua 脚本的统一管理

#### 2.4.1 Key 统一管理：一个 Key 目录，杜绝随处拼字符串

企业项目不会把 `"note_backbond:note:blocks:"` 这类字面量散落在各 Service 里——一旦格式调整或拼错，会污染所有引用方且极难排查。统一做法是**用一个 `RedisKey` 枚举当"key 目录"**：所有 key 模板只在枚举里定义一次，业务侧通过 `build(...)` 生成，格式由 2.3 强制。

```java
/**
 * Redis Key 目录：全项目唯一的 key 定义点。
 * 统一格式：note_backbond:<业务域>:<实体类型>:<标识符>[:<子标识符>]
 * 参数按 pattern 中的 %s 顺序由 build(...) 填充。
 */
public enum RedisKey {

    // ---- 临时/安全（1.1 现有，保持口径一致）----
    CAPTCHA      ("note_backbond:captcha:%s"),            // 图形验证码，参数 uuid
    EMAIL_CODE   ("note_backbond:email:code:%s"),         // 邮箱验证码，参数 email
    LOGIN_FAIL   ("note_backbond:login:fail:%s"),         // 登录失败计数，参数 account
    LOGIN_LOCK   ("note_backbond:login:lock:%s"),         // 账号锁定，参数 account
    LOGOUT_TOKEN ("note_backbond:logout:token:%s"),       // Token 注销黑名单

    // ---- 业务缓存（3.x 各节）----
    NOTE_BLOCKS  ("note_backbond:note:blocks:%s"),        // 笔记块 Hash，参数 noteId（3.1）
    NOTE_LIST    ("note_backbond:note:list:user:%s:page:%s:size:%s"), // 笔记列表分页，参数 userId,page,size（3.2）
    NOTEBOOK_LIST("note_backbond:notebook:list:user:%s"), // 笔记本列表，参数 userId（3.3）
    USER_INFO    ("note_backbond:user:info:%s"),          // 用户信息，参数 userId（3.4）
    TAG_LIST     ("note_backbond:tag:user:%s"),           // 标签列表，参数 userId（3.5）

    // ---- 锁 / 限流 ----
    PAGE_LOCK    ("note_backbond:lock:page:%s:%s"),       // 列表页互斥锁，参数 notebookId,userId（3.2）
    RATE_LIMIT   ("note_backbond:rate:limit:%s"),         // 接口限流，参数 key（3.7）
    ;

    private final String pattern;

    RedisKey(String pattern) { this.pattern = pattern; }

    /** 按参数顺序填充，例如 RedisKey.NOTE_BLOCKS.build(100) -> "note_backbond:note:blocks:100" */
    public String build(Object... args) {
        return String.format(pattern, args);
    }
}
```

业务侧用法：

```java
String blocksKey = RedisKey.NOTE_BLOCKS.build(noteId);        // 替代 "note_backbond:note:blocks:" + noteId
String pageLock  = RedisKey.PAGE_LOCK.build(notebookId, userId);
```

**好处**：
- **一处定义**：改格式/前缀只动枚举，全项目生效；
- **编译期纠错**：`build` 是方法，拼错枚举名编译不过，杜绝"手滑多打个冒号"这类 bug；
- **自带目录**：枚举就是全量 key 清单，做监控、批量清理、核对 key 口径时一眼看全；
- **TTL 同样集中**：单独一个常量类统一维护，避免各 Service 各写各的 TTL：

```java
public final class RedisTtl {
    private RedisTtl() {}
    public static final long NOTE_BLOCK     = 30;   // 分钟
    public static final long NOTE_LIST_P1   = 5;    // 分钟（首页）
    public static final long NOTE_LIST_PN   = 2;    // 分钟（翻页）
    public static final long USER_INFO      = 30;   // 分钟
    public static final long NOTEBOOK_LIST  = 10;   // 分钟
    public static final long TAG_LIST       = 30;   // 分钟
}
```

> 可选升级：再包一层 `RedisCacheService`（内部用 StringRedisTemplate + JSON + TTL），把"构建 key、GET/SET、过期"收敛到基础设施层；本项目用**枚举 + 直接调 StringRedisTemplate** 已足够。

#### 2.4.2 Lua 脚本：Spring `RedisScript` Bean + classpath 脚本文件

版本 CAS 这类"读-比较-写"必须是**原子脚本**，不要用 Java 先读后写。企业标准做法：Lua 脚本放 `src/main/resources/lua/`，用 `DefaultRedisScript` 注册成 Bean，执行时只传 key 和参数。

```java
@Configuration
public class RedisConfig {

    /** 版本 CAS 脚本（逻辑见 3.1.1）：确保落到缓存的一定是最新版本 */
    @Bean
    public RedisScript<Long> updateBlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/update_block.lua")));
        script.setResultType(Long.class);   // Lua 返回 1/0
        return script;
    }

    /** 写路径冷状态兜底（见 3.1.2）：原子续期 TTL / 补 __creating__ 占位，消除 hasKey→HSET 的过期竞态 */
    @Bean
    public RedisScript<Long> ensureCreatingScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/ensure_creating.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
```

资源目录：

```
src/main/resources/
└── lua/
    ├── update_block.lua      # 版本 CAS 脚本（读路径回填），完整内容见 3.1.1
    └── ensure_creating.lua   # 写路径冷状态兜底：原子续期 TTL / 补 __creating__ 占位
```

执行（业务侧封装）：

```java
@Autowired
private RedisScript<Long> updateBlockScript;   // 按字段名注入，多脚本不冲突

@Autowired
private RedisScript<Long> ensureCreatingScript;

// KEYS 只传 hashKey，其余全走 ARGV（Redis Cluster 下保证 key 落在同一槽）
private boolean luaCas(String hashKey, String blockId, String value,
                       int version, String mode, boolean allowAbsent) {
    Long r = stringRedisTemplate.execute(updateBlockScript,
            List.of(hashKey),                          // KEYS[1]
            blockId, value, version, mode, allowAbsent ? 1 : 0);   // ARGV[1..5]
    return r != null && r == 1L;
}

// 写标记前调用：原子保证 Hash 存在且带 __creating__ 占位/TTL（见 3.1.2）
private void ensureCreating(String hashKey, long ttlSeconds) {
    stringRedisTemplate.execute(ensureCreatingScript,
            List.of(hashKey), ttlSeconds);             // KEYS[1] / ARGV[1]
}
```

**注意点**：
- **脚本外置**（`ClassPathResource("lua/xxx.lua")`）：改 Lua 逻辑不用改 Java、不用重新编译；
- **`KEYS` 与 `ARGV` 严格区分**：key 放 `KEYS`，业务参数放 `ARGV`，不要把参数拼进 key 字符串——这是 Redis Cluster 下脚本能在同一槽执行的前提；
- **结果类型**：`RedisScript<Long>`，脚本 `return 1/0`；
- 多个 Lua 脚本就多注册几个 `@Bean`，一一对应 `lua/` 下的文件。

---

## 3. 缓存场景详细设计

### 3.1 笔记内容缓存（最高优先级）

#### 3.1.0 设计决策：每篇笔记一个 Redis Hash，写时打脏标记、读时懒回填

**背景**：`GET /noteBlock/getBlocks` 读取笔记全部块；`POST /noteBlock/batchUpdate` 是**增量更新**——比如 id=1 的笔记更新了 3 个块，SQL 只 UPDATE 这 3 行（`NoteBlockService.updateNoteBlocks` 走乐观锁 + 批量 UPDATE），而不是整篇重写。插入、删除也都是独立的批量事件。

**数据结构**：

```
note_backbond:note:blocks:<noteId>   Hash   field=块ID   value=块内容 JSON 或 脏标记 "version:N"
```

- 每篇笔记**一个 Hash key**，field 是块 ID（前端分配的 UUID）；
- value 有两种：
  - **块内容 JSON**：该块最近一次成功回填的数据（内含 `version` 乐观锁版本号）；
  - **脏标记 `version:N`**：该块在 DB 已更新到版本 N，缓存还没回填 N 的数据（N 就是脏标记携带的版本号）；
- 写路径不直接回填内容，只把变化的块置成脏标记（见 3.1.2）；
- 读路径取回全部 field 后在**内存按 order_key 排序**（Hash 不保序，见 3.1.4）。

**与其他方案对比**：

| 维度 | 方式A：整篇缓存（1 个 key 存整篇 JSON 数组） | 方式B：逐块 key + 顺序索引 | 方式C：单 Hash + 版本号脏标记 ✅ |
|------|------|------|------|
| 写入失效 | 任意一块变化 → 删整篇 key | 只写变化的块 | 只把变化的块置脏标记（CAS），O(1) |
| 编辑→关闭→重开 | 每次穿透 DB（编辑期间 key 反复被删） | 未变块仍命中，只重写变化块 | 未变块仍命中，只回填脏块 |
| 并发正确性 | 删 key 天然安全 | 需处理写缓存顺序 | **版本 CAS，旧回填写不进去** |
| Redis key 数量 | 1 篇 1 个 | 1 篇 ~21 个 | **1 篇 1 个** |
| TTL 粒度 | 整篇 | 每块各自（易散落过期） | **整篇统一过期** |
| 写路径 | 删 key | 序列化 + SET 块 | **CAS 置脏标记，零序列化** |
| 读路径 | 1 次 GET | GET 索引 + MGET | HGETALL + CAS 回填脏块 + 内存排序 |

**结论**：采用方式C，并对"打脏标记"做版本号 CAS 加固（见 3.1.1）——这是**唯一**能保证"缓存最终一定是最新内容"的写法。

#### 3.1.1 竞态分析：为什么脏标记必须带版本号 + CAS

**空串脏标记的缺陷**（不解决就会出现的竞态）：

1. 线程A 更新块 → DB 到版本 2 → 缓存置脏标记（空串），A 退出；
2. 线程B 读，发现脏 → 查 SQL 拿到版本 2 的数据，准备回填缓存；
3. 线程C 又更新同一块 → DB 到版本 3 → 缓存置脏标记（空串），C 退出；
4. 此刻 B 把版本 2 的数据写回缓存 → **覆盖了 C 的脏标记，缓存里存的是旧版本 2**；
5. 之后所有读都命中"非脏"的旧数据，而 DB 已是版本 3 → 后续更新走乐观锁（基于版本 2）**全部失败**，该块被卡死直到缓存过期。

**解决方案：脏标记携带版本号 + CAS 原子写入**

- 脏标记 = `version:N`（N 为 DB 乐观锁版本），不再是空串；
- **读回填条件**：`Redis.version <= DB.version` 才允许写入缓存（旧版本永远覆盖不了新版本）；
- **写脏标记条件**：`DB.version > Redis.version` 才允许写入（避免把同版本真实数据降级成脏标记）；
- 读版本 + 比较 + 写入在**同一个 Lua 脚本**里原子完成，杜绝"先读后写"的竞态窗口。

```lua
-- 版本 CAS：确保落到缓存的一定是最新版本
-- KEYS[1]=hashKey  ARGV[1]=blockId  ARGV[2]=newValue  ARGV[3]=newVersion
-- ARGV[4]=mode("data"/"dirty")  ARGV[5]=allowAbsent(0/1，仅 data 模式生效)
-- 返回 1=写入成功  0=CAS 拒绝（缓存已有更新版本 / 块已删除）
local cur = redis.call('HGET', KEYS[1], ARGV[1])
local curVer = 0
local absent = (cur == false)
if not absent then
    local dv = string.match(cur, '^version:(%d+)$')          -- 脏标记 version:N
    if dv then
        curVer = tonumber(dv)
    else
        local ok, obj = pcall(cjson.decode, cur)             -- 真实数据 JSON 里的 version
        if ok and obj and obj.version then curVer = tonumber(obj.version) end
    end
end

local newVer = tonumber(ARGV[3])
if ARGV[4] == 'dirty' then
    -- 写脏标记：field 不存在 = 新插入的块 → 写入；已存在 → DB 版本必须严格大于缓存版本
    if absent or newVer > curVer then
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
        return 1
    end
else
    -- 读回填：field 不存在时，仅冷启动全量回填（allowAbsent=1）允许写入；
    -- 完整 Hash 上 field 不存在 = 块已删除 → 拒绝
    if absent then
        if tonumber(ARGV[5]) == 1 then
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            return 1
        end
    elseif newVer >= curVer then
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
        return 1
    end
end
return 0
```

> 语义约定：**field 不存在 = 块已删除/不存在**；**"没回填" = field 存在但 value 是 `version:N`**。下文代码里的 `luaCas(hashKey, blockId, newValue, newVersion, mode, allowAbsent)` 就是这段脚本的 Java 封装（返回 `true/false`）。`allowAbsent` 只对 `data` 模式生效：`false` = 完整 Hash 上 field 不存在即视为已删除，跳过；`true` = 冷启动全量回填，field 不存在视为还没写入，允许写入。

**重放上面的竞态**：B 回填版本 2（expect=2）时，缓存当前是 C 写的脏标记 `version:3`（3）→ `2 >= 3` 不成立 → CAS 拒绝写入，B 的旧数据落不进缓存；缓存保持 `version:3`，下一次读用版本 3 回填。**最终缓存一定是最后一次更新的内容。**

**为什么不用"延时双删"**：延时双删靠"删缓存 + 延迟 N 毫秒再删一次"兜底，两次删除之间旧回填仍可能写入，不一致窗口取决于延迟时间且依赖定时任务；版本 CAS 让旧回填**从机制上写不进去**，不一致窗口只有一次 CAS 往返，无定时任务、自愈更快。

#### 3.1.2 写路径（batchUpdate）——版本 CAS 打脏标记

在 `NoteBlockBatchService.updateNoteBlocksDiff` 现有 DB 逻辑**成功提交后**，按 diff 增量同步缓存：

```java
String hashKey = "note_backbond:note:blocks:" + noteId;

// 写路径不做 singleflight 串行（保证写操作完全并发，不被读回填阻塞）：
// 更新/插入的竞态已由版本 CAS 兜住；删除的极窄边角用延时双删兜底（见 3.1.4）。

// 0. 冷状态兜底（原子）：
//    Redis 的 Hash 有 TTL，直接用 hasKey 判断再 HSET 存在 TOCTOU 竞态——hasKey 返回 true 后、
//    HSET 之前 key 恰好过期，会把"只含一个块的残缺 Hash"写进缓存（且没有 __creating__ 占位），
//    读路径会误以为笔记只有这一个块。
//    ensureCreating 用 Lua 原子地"续期 TTL / 补 __creating__ 占位"，保证后续标记落在
//    "确定存在、TTL≥30min" 的 Hash 上（脚本见下方，说明见 3.1.4）：
ensureCreating(hashKey, 30 * 60);

// ① 更新块：成功 → CAS 脏标记 version:N（N 为 DB 写入后的新版本号，乐观锁自增后的值）
for (NoteBlock b : 成功更新的块) {
    luaCas(hashKey, b.getId(), "version:" + b.getVersion(), b.getVersion(), "dirty", false);
}
// ② 插入块：成功 → data 模式直接写【完整块 JSON】（allowAbsent=true）。
//    插入不走脏标记（方案 A）：新块数据已在请求里，直接落缓存省一次回填；
//    前置 ensureCreating 保证 Hash 带 __creating__ 占位或已完整，绝不会创建"无占位 + 只有一块"的残缺 Hash
for (NoteBlock b : 成功插入的块) {
    b.setVersion(1);   // 新插入块 DB 版本固定 1（见 batchInsertBlock），缓存数据同步
    luaCas(hashKey, b.getId(), JSON.toJSONString(b), 1, "data", true);
}
// ③ 删除块：成功 → HDEL 该 field（field 消失，与 DB 删除同步）
//    极端场景（冷启动回填并发删除）可用"延时双删"兜底：延迟 ~500ms 异步再 HDEL 一次
for (NoteBlock b : 成功删除的块) {
    redisTemplate.opsForHash().delete(hashKey, b.getId());
}
```

`ensure_creating.lua` 内容：

```lua
-- 原子保证：写标记前 Hash 一定存在且带 __creating__ 占位/TTL，消除 hasKey→HSET 的过期竞态
-- KEYS[1]=hashKey  ARGV[1]=ttl（秒）
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))   -- 已存在 → 续期（不存在的 key 是 no-op）
if redis.call('EXISTS', KEYS[1]) == 0 then          -- 真不存在 → 补占位
    redis.call('HSET', KEYS[1], '__creating__', '1')
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return 1
```

> 原理：`EXPIRE` 对不存在的 key 是 no-op；先续期、再判断、再补占位都在一个原子脚本里。执行后 Hash 要么刚被创建（带 `__creating__`），要么被续期到 ≥30min，随后的 `luaCas` 标记操作落在这个确定存在、不会马上过期的 Hash 上。

**与 DB 增量更新的对应关系**：

| diff 事件 | DB 操作 | 缓存操作 |
|----------|---------|---------|
| 更新块 | `UPDATE ... WHERE id AND version`（只更新变化行） | `luaCas` 脏标记 `version:N` |
| 新增块 | `INSERT` | `luaCas` data 写完整块 JSON（allowAbsent=true，前置 ensureCreating） |
| 删除块 | `DELETE` | `HDEL` 该 field |

**关键点**：

1. **只标记成功的块**：乐观锁冲突（`failUpdateBlocks`）的块不碰缓存，保持旧值，客户端刷新重读即可；
2. **脏标记 CAS 用严格 `>`**：若缓存已是同版本真实数据（罕见竞态），不降级、直接跳过；
3. **写缓存失败兜底**：Lua/HDEL 失败重试一次；仍失败则 `DEL` 整个 Hash（保守降级，下次读全量回填）；DB 已提交结果不受影响；
4. 若 DB 提交失败（事务回滚），不执行任何缓存同步，天然一致。

**为什么写路径"打脏标记"而不是"直接回填内容"？**

| | 打脏标记（推荐） | 直接回填 |
|------|------|------|
| 写路径开销 | CAS 置脏标记，O(1)，零序列化 | JSON 序列化整块 + HSET 内容 |
| 与块 JSON 结构解耦 | 写路径不关心块长什么样 | 块结构变了写路径也要改 |
| 连续编辑同一块 | 每次都只是 O(1) 标记 | 每次都序列化 |
| 代价 | "编辑→重开"命中一次针对脏块 id 的主键小查询（`WHERE id IN (...)`，几百微秒） | 无 |

结论：autosave 是最高频写路径，把序列化从写路径挪到读路径（回填时才序列化），写路径保持最轻；代价是重开时对脏块做一次主键小查询，对个人笔记场景无感知。

> 例外：**插入块不走脏标记，直接写完整数据**（见 3.1.2 ②）——插入低频且新块数据在请求里已是全量，直接落缓存省一次回填；更新/重排仍打脏标记（autosave 高频，省序列化）。注意：插入的 `data` 写必须**前置 `ensureCreating`**，否则 Hash 过期时 `HSET` 会创建"无 `__creating__` 占位 + 只有一块"的残缺 Hash。

#### 3.1.3 读路径（getBlocks）——脏块识别 + CAS 回填 + 失败自旋

```java
public List<NoteBlock> getNoteBlocks(Integer noteId) {
    String hashKey = "note_backbond:note:blocks:" + noteId;
    Map<Object, Object> hash = redisTemplate.opsForHash().entries(hashKey);

    // 1. 冷启动 / 创建中（写路径打的占位）→ 单飞全量回填
    if (hash.isEmpty() || hash.containsKey("__creating__")) {
        return singleFlight(hashKey, () -> backfillAll(noteId));
    }

    // 2. 找出脏块（值以 "version:" 开头）
    List<String> dirtyIds = hash.entrySet().stream()
            .filter(e -> ((String) e.getValue()).startsWith("version:"))
            .map(e -> (String) e.getKey()).toList();
    if (dirtyIds.isEmpty()) {
        return assembleSorted(hash, dirtyIds);
    }

    // 3. 单飞回填脏块：返回 SQL 查到的脏块数据（本次响应用它），并 CAS 回填缓存
    List<NoteBlock> dirtyBlocks = singleFlight(hashKey,
            () -> backfillFields(hashKey, dirtyIds));

    // 4. 干净块来自缓存 + 脏块来自 SQL，合并后按 order_key 排序返回
    List<NoteBlock> all = assembleSorted(hash, dirtyIds);   // 跳过脏块
    all.addAll(dirtyBlocks);
    all.sort(Comparator.comparing(NoteBlock::getOrderKey));
    return all;
}
```

**冷启动回填（整篇，同样逐块 CAS）**：

```java
private List<NoteBlock> backfillAll(Integer noteId) {
    String hashKey = "note_backbond:note:blocks:" + noteId;
    List<NoteBlock> blocks = blockService.selectNoteBlocks(noteId); // 已 ORDER BY order_key
    if (blocks.isEmpty()) {
        // 空笔记：短 TTL 空标记，防穿透
        redisTemplate.opsForHash().put(hashKey, "__empty__", "1");
        redisTemplate.expire(hashKey, 60, TimeUnit.SECONDS);
        return blocks;
    }
    for (NoteBlock b : blocks) {
        // 逐块 CAS：冷启动是完整 Hash 的第一个写入方，field 不存在 = 还没写入（allowAbsent=true）；
        // 若回填期间有并发写已打入更新的脏标记，CAS 自动跳过该块，不会覆盖
        luaCas(hashKey, b.getId(), JSON.toJSONString(b), b.getVersion(), "data", true);
    }
    redisTemplate.opsForHash().delete(hashKey, "__creating__");   // 清占位
    redisTemplate.expire(hashKey, 30, TimeUnit.MINUTES);
    return blocks;   // 本次响应返回 SQL 快照
}
```

**脏块回填（CAS + 失败自旋）**：

```java
private List<NoteBlock> backfillFields(String hashKey, List<String> dirtyIds) {
    Map<String, NoteBlock> result = new LinkedHashMap<>();
    List<String> pending = new ArrayList<>(dirtyIds);
    for (int round = 0; round < 5 && !pending.isEmpty(); round++) {   // 自旋上限，防写风暴死循环
        // 1. 查 SQL 拿脏块最新数据（含 DB 版本）
        List<NoteBlock> blocks = blockMapper.selectBatchIds(pending);
        Set<String> found = blocks.stream().map(NoteBlock::getId).collect(Collectors.toSet());
        List<String> stillDirty = new ArrayList<>();
        for (NoteBlock b : blocks) {
            // 2. CAS 回填：完整 Hash 上 field 不存在 = 块已删除（allowAbsent=false）→ 跳过
            boolean ok = luaCas(hashKey, b.getId(), JSON.toJSONString(b),
                    b.getVersion(), "data", false);
            if (ok) {
                result.put(b.getId(), b);            // 写成功：本次响应用 SQL 快照
                continue;
            }
            // CAS 失败 → 重查缓存判断
            String cur = (String) redisTemplate.opsForHash().get(hashKey, b.getId());
            if (cur == null) {
                continue;                            // field 已不存在 = 块被并发删除，本次也不返回
            }
            if (cur.startsWith("version:")) {
                stillDirty.add(b.getId());           // 缓存仍是更新的脏标记 → 下轮重查 SQL
                result.put(b.getId(), b);            // 本次先用 SQL 快照（可接受的短暂旧值）
            } else {
                result.put(b.getId(), JSON.parseObject(cur, NoteBlock.class));  // 缓存有更新数据，直接用
            }
        }
        // DB 已不存在的脏 id（块被删除）→ HDEL，避免永远脏着
        for (String id : pending) {
            if (!found.contains(id)) redisTemplate.opsForHash().delete(hashKey, id);
        }
        pending = stillDirty;
        if (!pending.isEmpty()) ThreadUtil.sleep(20);   // 退避后重试
    }
    return new ArrayList<>(result.values());            // 自旋耗尽也返回最后的 SQL 快照
}
```

**组装（跳过脏块与标记字段，按 order_key 排序）**：

```java
private List<NoteBlock> assembleSorted(Map<Object, Object> hash, List<String> dirtyIds) {
    Set<String> dirty = new HashSet<>(dirtyIds);
    List<NoteBlock> blocks = new ArrayList<>();
    for (Map.Entry<Object, Object> e : hash.entrySet()) {
        String id = (String) e.getKey();
        String json = (String) e.getValue();
        if (dirty.contains(id)) continue;                       // 脏块由 SQL 数据提供
        if (json == null || json.isEmpty()) continue;
        if (json.startsWith("version:")) continue;              // 防御
        if ("__creating__".equals(id) || "__empty__".equals(id)) continue;
        blocks.add(JSON.parseObject(json, NoteBlock.class));
    }
    blocks.sort(Comparator.comparing(NoteBlock::getOrderKey));  // 等价于 SQL ORDER BY order_key
    return blocks;
}
```

**单飞（Singleflight）实现**：单实例部署用进程内 `ConcurrentHashMap`，不需要 Redis 分布式锁：

```java
private final ConcurrentHashMap<String, CompletableFuture<?>> loaders = new ConcurrentHashMap<>();

private <T> T singleFlight(String key, Supplier<T> loader) {
    CompletableFuture<T> future = new CompletableFuture<>();
    CompletableFuture<T> existing = (CompletableFuture<T>) loaders.putIfAbsent(key, future);
    if (existing != null) {
        return existing.join();                     // 已有线程在加载，等它的结果
    }
    try {
        T result = loader.get();
        future.complete(result);
        return result;
    } catch (Throwable t) {
        future.completeExceptionally(t);
        throw t;
    } finally {
        loaders.remove(key);
    }
}
```

> 单飞只用于**读路径**（冷启动/脏块回填），写路径不串行，保证写操作完全并发。若未来多实例部署，把读回填换成 Redis `SET NX EX` 互斥锁 + 双检即可（复用 3.2 的锁工具）。

#### 3.1.4 完整性约束与冷启动

**完整性约束**（为什么读路径不会读到残缺数据）：Hash 的 field 集合始终 = DB 中该笔记现有块集合，靠以下规则保证：

- Hash 由 `backfillAll` 全量创建，一次性写入全部 field；
- **冷状态写路径不直接写块**，通过 `ensureCreating`（Lua 原子）打 `__creating__` 占位，读路径见到占位就全量回填——避免"写一个块 → Hash 只有它一个 field → 读路径误以为笔记只有一个块"。原子性很关键：若用 `hasKey` 判断再 HSET，Hash 可能在判断后、写入前过期，产生**没有占位的残缺 Hash**（见 3.1.2）；
- 删除块的 HDEL 与 DB 删除同步，field 集合随之缩小；
- 每个 field 的写入都过版本 CAS，旧版本不可能覆盖新版本。

**冷启动与首次写入并发**：写路径在 Hash 不存在时打 `__creating__` 占位并对该块 CAS 脏标记；回填线程 SELECT 期间若有并发写，要么回填的 CAS 被更新的脏标记挡下，要么写的 CAS 脏标记覆盖旧回填——两种交错都收敛到最新版本，不会出现"回填了旧数据且无人知晓"的持久不一致。

> **已知边角：冷启动回填 vs 并发删除**。语义上 field 不存在 = 块已删除（"没回填"是 field 存在但值为 `version:N`），`backfillFields`（allowAbsent=false）在完整 Hash 上遇到 field 不存在会跳过，因此**稳态的删除-回填竞态是安全的**；唯一残余在 `backfillAll` 冷启动（allowAbsent=true）：
>
> | 步骤 | 线程 | 动作 |
> |---|---|---|
> | 1 | 读者R | `getBlocks` → Hash 不存在 → 全量回填 → `SELECT` 快照含块 X@v2 |
> | 2 | 写者W | 删除 X：DB `DELETE` 成功 → `HDEL hash X` |
> | 3 | 读者R | CAS 写 X：`HGET` → nil → allowAbsent=true → **把 X 写回缓存（幽灵）** |
> | 4 | 读者E | 读缓存 → 返回已删除的 X，直到 TTL 过期（~30min） |
>
> 概率极低（冷启动恰好与删除并发、且快照含该块），个人笔记单人编辑场景可忽略；若在多人协作等极端场景想再收窄，可对**删除操作**用**延时双删**兜底：`HDEL → 延迟 ~500ms（异步）→ 再 HDEL`，第二个 HDEL 落在并发回填完成之后，把可能写回的幽灵清掉。

**为什么顺序不用 Redis ZSet / List**：`note_block.order_key` 是 `varchar(32)` 字典序字符串（`'n' / 'zz' / 'zzzz'`），ZSet 的 double score 放不下（需 ~150bit 精度）；Hash 本身不保序。所以读取时取回全部块后**在内存按 order_key 排序**（一篇 ≤50 块，开销可忽略），语义与 `ORDER BY order_key` 完全一致。

#### 3.1.5 TTL 与内存估算

- TTL 30min，**整篇 Hash 一起过期**（`EXPIRE` 作用于整个 key），不存在逐块散落过期问题；空笔记短 TTL 60s；
- 内存：单块 JSON ~500B + Redis field 开销 ~50B ≈ 550B。10 万篇活跃笔记 × 20 块 ≈ 1.1GB，加每篇 1 个 Hash 的 key 开销（~10 万 × ~200B ≈ 20MB），与逐块方案基本持平，可接受。

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

> 互斥锁用简单 `SET NX EX` 实现的工具类即可（约 20 行），不引入 Redisson；竞争只发生在"同一列表首次回源"时，锁粒度按 `notebookId:userId`。

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

**设计方案**（纯 Redis Cache Aside，不引入本地缓存）：

```java
// 用户信息 30 分钟缓存
// 注意：用户信息变更频率极低，但读取频率高（每次请求都可能用来校验权限）
public User getUserInfo(Integer userId) {
    String cacheKey = "note_backbond:user:info:" + userId;
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return JSON.parseObject(cached, User.class);
    }
    User user = userService.getUserInfo(userId);
    if (user != null) {
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(user), 30, TimeUnit.MINUTES);
    }
    return user;
}
```

**TTL**：30 分钟。

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

### 3.6 并发编辑策略（已删除"分布式锁"方案）

本项目**单实例部署、笔记由单人编辑**，`batchUpdate` 的并发冲突交给现有的**乐观锁 + 冲突回传重试**即可——`updateNoteBlocks` 返回失败的块 ID（`failUpdateBlocks`），客户端收到冲突后刷新重读，无需服务端加分布式锁。

结论：**不引入 Redisson 分布式锁**，保持依赖与复杂度最小。

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

// 2. Redis 宕机兜底：本项目不引入本地缓存（Caffeine），Redis 不可用时请求直接走 DB，
//    靠下面两层保护 DB：
//    a. 限流（3.7）：削减瞬时流量
//    b. DB 熔断器：超过最大并发直接拒绝，保护 DB 不被击穿

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

**对本项目块缓存的落地**：单 Hash + **版本号脏标记**方案，一致性靠"**DB 先提交、缓存后 CAS 打标记、读时 CAS 回填**"：

1. 脏标记携带 DB 乐观锁版本号（`version:N`），**所有缓存写入都过版本 CAS**——旧版本永远覆盖不了新版本，保证最终落到缓存的一定是最后一次更新的内容；
2. 只标记**乐观锁成功**的块（冲突块保持旧缓存，客户端刷新重读）；
3. 打标记/回填失败重试一次，仍失败 → 兜底 `DEL` 整个 Hash（下次读全量回填）；
4. TTL 30min 作为最终一致性兜底，极端情况下短暂读到旧值也能自愈。

> 延时双删在"先删缓存再写 DB + 并发读回写"下需要两次删除兜底，且两次删除之间旧回填仍可能写入，不一致窗口取决于延迟时间；版本 CAS 让旧回填**从机制上写不进去**，不一致窗口只有一次 CAS 往返，无定时任务、自愈更快。

---

## 5. 逐出与过期策略

### 5.1 Redis 逐出策略

```
maxmemory-policy allkeys-lru
```

**理由**：笔记数据天然具有"热点"属性（最近编辑的笔记最常被读取），LRU 策略最适合。

### 5.2 Redis 客户端配置要点（Lettuce 连接池）

不引入 Caffeine 后，缓存性能依赖 Redis 连接池与序列化配置：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3s
      lettuce:
        pool:
          max-active: 32        # 最大连接数
          max-idle: 16
          min-idle: 4
          max-wait: 3s          # 获取连接超时
```

> 序列化统一使用 `StringRedisTemplate` + JSON（本项目已用 Redis 做验证码等场景，沿用同一客户端），**不要用 JDK 序列化**（`JdkSerializationRedisSerializer`），避免类型兼容性问题与体积膨胀。

### 5.3 各场景缓存参数汇总

| 缓存场景 | Redis TTL | 最大容量 | 失效时机 |
|----------|-----------|---------|---------|
| 笔记块（单 Hash） | 30min + 随机偏移 | 按篇，无上限（受 LRU 约束） | 块更新/删除时 CAS 打脏标记，读时 CAS 回填 |
| 笔记列表 | 首页5min/翻页2min | 500 条 | 创建/删除/移动笔记 |
| 笔记本列表 | 10min | 200 条 | 创建/删除笔记本 |
| 用户信息 | 30min | 500 条 | 更新用户信息 |
| 标签列表 | 30min | 200 条 | 创建/删除标签 |
| 空值标记 | 30~60s | 100 条 | 自动过期 |

---

## 6. 监控与可观测性

首版**不引入 Micrometer / Prometheus 监控体系**（避免为缓存引入整套监控依赖），用轻量手段即可：

| 手段 | 说明 |
|------|------|
| Redis 原生监控 | `INFO memory`、`INFO stats`、`SLOWLOG`：观察内存、命中、慢命令 |
| 应用日志打点 | 缓存读写处打日志（命中 / 未命中 / 回源），配合现有日志框架排查 |
| 脏块回填计数 | 3.1 读路径检测到 `version:` 前缀脏标记触发回填，可计数后输出到日志 |

需要关注的指标（通过上述手段观测）：

| 指标 | 告警阈值 | 含义 |
|------|---------|------|
| Redis 内存 | > 80% | 检查 LRU 是否生效 |
| 回源比例 | 明显上升 | 缓存可能失效/穿透 |
| DB 连接数 | > 80% | DB 压力过大，缓存可能已失效 |

流量起来后再评估引入 Prometheus + Grafana。

---

## 7. 实施优先级

| 优先级 | 场景 | 工作量 | 收益 | 说明 |
|--------|------|--------|------|------|
| P0 | 接口限流 | 小 | 保障系统稳定性 | 必须先做，防止被刷 |
| P1 | 笔记内容缓存（Hash 逐块） | 中 | 降低 NoteBlock 查询 90% | 最高频读取接口，贴合增量更新 |
| P1 | 笔记列表缓存 | 中 | 降低 LEFT JOIN 查询 80% | 最重查询 |
| P2 | 笔记本列表缓存 | 小 | 降低查询 70% | 数据量小，实现简单 |
| P2 | 用户信息缓存 | 小 | 降低查询 90% | 变更少，读取多 |
| P2 | 标签缓存 | 小 | 降低查询 80% | 数据量小 |
| P3 | 防击穿/雪崩 | 中 | 保障极端场景稳定 | 流量达到一定规模后再做 |
| P3 | 缓存预热 | 小 | 提升首次请求体验 | 用户感知不明显 |
| P3 | 监控告警 | 大 | 可观测性 | 基础设施，长期受益 |

### 推荐实施步骤

**第一阶段（P0）**：接口限流 → 1 天
**第二阶段（P1）**：笔记内容缓存（Hash 逐块）+ 笔记列表缓存 → 3 天
**第三阶段（P2）**：笔记本列表 + 用户信息 + 标签缓存 → 2 天
**第四阶段（P3）**：防崩溃加固 + 监控告警 → 持续迭代

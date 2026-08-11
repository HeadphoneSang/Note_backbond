package org.template.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.template.enums.RedisKey;
import org.template.model.NoteBlock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class BlockRedisService {

    @Value("${block.redis.ttl}")
    private Long hashTTL;

    @Value("${block.redis.coldPlaceholder}")
    private String coldPlaceholder;

    @Value("${block.redis.dirtyPlaceholder}")
    private String dirtyPlaceholder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** update_block.lua（版本 CAS） */
    @Autowired
    @Qualifier("updateBlockScript")
    private RedisScript<Long> updateBlockScript;

    /** ensure_creating.lua（写路径冷状态原子兜底） */
    @Autowired
    @Qualifier("ensureCreatingScript")
    private RedisScript<Long> ensureCreatingScript;

    @Autowired
    private ObjectMapper objectMapper;

    private final String singleBlocksQueryKey = "noteBlocks:%d";

    private final String singleDirtyQueryKey = "dirtyBlocks:%d";
    private final ConcurrentHashMap<String,CompletableFuture<?>> futureQueries = new ConcurrentHashMap<>();

    @PostConstruct
    public void testRedis(){
        redisTemplate.opsForValue()
                .set("test:key", "hello");

        System.out.println(
                redisTemplate.opsForValue()
                        .get("test:key")
        );
    }

    private boolean luaCas(String hashKey, String blockId, String value,
                           int version, String mode, boolean allowAbsent) {
        Long r = redisTemplate.execute(updateBlockScript,
                List.of(hashKey),                          // KEYS[1]
                blockId, value, String.valueOf(version), mode, String.valueOf(allowAbsent ? 1 : 0));   // ARGV[1..5]
        return r != null && r == 1L;
    }

    /**
     * 写标记前必调：原子保证 Hash 存在且带 __creating__ 占位/TTL。
     * 防止"Hash 过期后，单块 HSET 直接创建出【无占位】的残缺 Hash"（见方案书 3.1.2）。
     */
    private void ensureCreating(String hashKey, long ttlSeconds) {
        redisTemplate.execute(ensureCreatingScript, List.of(hashKey), String.valueOf(ttlSeconds));
    }

    /**
     * 插入新块：data 模式 + allowAbsent=true，直接写【完整块 JSON】（方案 A）。
     * 前置 ensureCreating：Hash 不存在时先打 __creating__ 占位，
     * 因此插入后的 Hash 要么带占位（读路径全量回填）、要么本就完整，绝不会是"无占位 + 只有一块"。
     */
    private void cacheBlock(String hashKey,NoteBlock block) {
        try {
            luaCas(hashKey, block.getId(), objectMapper.writeValueAsString(block), 1, "data", true);
        } catch (Exception e) {
            throw new IllegalStateException("笔记块序列化失败", e);
        }
    }

    public void cacheNewBlock(NoteBlock block){
        assert block != null && block.getNoteId() != null;
        String hashKey = RedisKey.NOTE_BLOCKS.build(block.getNoteId());
        // 确保对应的hash是否存在，不存在的话创建一个
        ensureCreating(hashKey, hashTTL);
        cacheBlock(hashKey,block);
    }

    public void cacheNewBlocks(int noteId,List<NoteBlock> blocks){
        assert blocks != null;
        String hashKey = RedisKey.NOTE_BLOCKS.build(noteId);
        // 确保对应的hash是否存在，不存在的话创建一个
        ensureCreating(hashKey, hashTTL);
        for(NoteBlock block : blocks){
            cacheBlock(hashKey,block);
        }
    }

    /**
     * 将指定的blocks都置为脏标记，
     * 如果指定的块缓存不存在，就什么也不做，静默，等待查询的时候一起加入
     * 如果指定的块缓存存在，但是已经是脏标记，就检查自己的脏标记是不是晚于已有的，如果是就覆盖，如果不是就不动
     * 如果指定的块缓存存在，而且不是脏标记，就覆盖为脏标记
     * @param noteId hash id
     * @param blocks 块列表
     */
    public void dirtyBlocks(int noteId, List<NoteBlock> blocks){
        assert blocks!=null;
        String hashKey = RedisKey.NOTE_BLOCKS.build(noteId);
        String dirtyTalk = "version:%d";
        for(NoteBlock block : blocks){
            int version = block.getVersion();
            boolean isSuccess = luaCas(hashKey, block.getId(), String.format(dirtyTalk,version),version,"dirty",false);
            System.out.println("block:"+block.getId()+isSuccess);
        }
    }

    public void deleteBlocks(int noteId,List<NoteBlock> blocks){
        assert blocks!=null;
        String hashKey = RedisKey.NOTE_BLOCKS.build(noteId);
        for(NoteBlock block: blocks){
            redisTemplate.opsForHash().delete(hashKey,block.getId());
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> T singleFlyQuery(String queryKey,Supplier<T> supplier){
        CompletableFuture<T> queryFuture = new CompletableFuture<>();

        CompletableFuture<T> existedQueryFuture = (CompletableFuture<T>)futureQueries.putIfAbsent(queryKey,queryFuture);
        if (existedQueryFuture!=null){
            return existedQueryFuture.join();
        }
        try{
            T result = supplier.get();
            queryFuture.complete(result);
            return result;
        }catch (Throwable e){
            queryFuture.completeExceptionally(e);
            throw e;
        }finally {
            futureQueries.remove(queryKey);
        }
    }

    private  Supplier<List<NoteBlock>> queryAndCache(
            Integer noteId,
            Supplier<List<NoteBlock>> fullQuery
    ){
        return () -> {
            List<NoteBlock> blocks = fullQuery.get();
            assert blocks!=null;
            String cacheKey = RedisKey.NOTE_BLOCKS.build(noteId);
            redisTemplate.execute(ensureCreatingScript, List.of(cacheKey), String.valueOf(hashTTL));
            System.out.println("=======================");
            System.out.println("缓存所有的块到redis开始");
            for (NoteBlock block:blocks){
                try {
                    luaCas(
                            cacheKey,
                            block.getId(),
                            objectMapper.writeValueAsString(block),
                            block.getVersion(),
                            "data",
                            true
                    );
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("缓存所有的块到redis结束");
            System.out.println("=======================");
            redisTemplate.opsForHash().delete(RedisKey.NOTE_BLOCKS.build(noteId),coldPlaceholder);
            return blocks;
        };
    }

    private Supplier<List<NoteBlock>> queryAndCacheDirty(
            String hashKey,
            List<String> dirtyIds,
            Function<List<String>,List<NoteBlock>> batchQuery
    ) {
        return () -> {
            List<String> pending = new ArrayList<>(dirtyIds);
            Map<String,NoteBlock> result = new LinkedHashMap<>();
            for (int i = 0;i<5&&!pending.isEmpty();i++){
                List<NoteBlock> batchBlocks = batchQuery.apply(pending);
                List<String> epochIds = new ArrayList<>(pending);
                pending.clear();
                for (NoteBlock block : batchBlocks){
                    boolean isCache;
                    try {
                        isCache = luaCas(
                                hashKey,
                                block.getId(),
                                objectMapper.writeValueAsString(block),
                                block.getVersion(),
                                "data",
                                false
                        );
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    if (!isCache){
                        // 可能两种情况，一个是块被删除了，一个是块又被更新了，我们sql快照也是旧版本
                        String cur = (String) redisTemplate.opsForHash().get(hashKey, block.getId());
                        if(cur==null){
                            continue;
                        }
                        if(cur.startsWith(dirtyPlaceholder)){
                            pending.add(block.getId());
                            // 记录旧值，用来防治自选失败兜底
                            result.put(block.getId(),block);
                        }
                    }else{
                        result.put(block.getId(),block);
                    }
                }
                // 检查我的当前轮次的脏id集合中，是否有已经在数据库中被删除的
                Set<String> realIds = batchBlocks.stream().map(NoteBlock::getId).collect(Collectors.toSet());
                for (String targetId : epochIds){
                    if (!realIds.contains(targetId)){
                        result.remove(targetId);
                        redisTemplate.opsForHash().delete(hashKey,targetId);
                    }
                }
                // 如果还有脏的需要查询，
                if (!pending.isEmpty()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
            }
            return result.values().stream().toList();
        };
    }

    private List<NoteBlock> assembleBlocks(Map<Object,Object> hashCache,List<String> dirtyIds){
        Set<String> dirtySet = new HashSet<>(dirtyIds);
        return hashCache.entrySet().stream().
                filter(entry->!dirtySet.contains((String)entry.getKey())).//过滤掉脏块
                map(entry->{//将缓存的块，转成对象返回
                    String json = (String) entry.getValue();
                    try {
                        return objectMapper.readValue(json,NoteBlock.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
    }

    /**
     * 从缓存中读取对应noteId的所有块，如果没有从数据库读取
     * @param noteId 目标笔记
     * @param fullQuery 全量从数据库查询对应笔记的所有块
     * @param batchQuery 根据块id列表，返回对应的块列表
     * @return 返回对应的块列表
     */
    public List<NoteBlock> getNoteBlocks(
            Integer noteId,
            Supplier<List<NoteBlock>> fullQuery,
            Function<List<String>,List<NoteBlock>> batchQuery
            ){
        String key = RedisKey.NOTE_BLOCKS.build(noteId);
        Map<Object,Object> hashCache = redisTemplate.opsForHash().entries(key);
        if (hashCache.isEmpty() || hashCache.containsKey(coldPlaceholder)){
            // 对应的hash不存在，冷启动，这里采用单服务的多sql请求合并操作，保证只有一个线程去请求sql，防止缓存雪崩
            System.out.printf("=======冷启动%d=======%n",noteId);
            return singleFlyQuery(
                    String.format(singleBlocksQueryKey,noteId),
                    queryAndCache(noteId,fullQuery)
            );
        }
        System.out.printf("=======命中缓存%d=======%n",noteId);
        // 检查hashCache的脏标记
        List<String> dirtyBlockIds = hashCache.entrySet().stream().filter(entry->{
            String value = (String)entry.getValue();
            return value.startsWith(dirtyPlaceholder);
        }).map(entry-> (String)entry.getKey()).toList();
        // 获得干净块的集合
        List<NoteBlock> cacheBlocks = assembleBlocks(hashCache,dirtyBlockIds);
        // 查询sql，回填cache
        List<NoteBlock> dirtyBlocks;
        if (!dirtyBlockIds.isEmpty()){
            System.out.printf("=======回表反哺redis%d=======%n",noteId);
            dirtyBlocks = singleFlyQuery(String.format(singleDirtyQueryKey,noteId),queryAndCacheDirty(key,dirtyBlockIds,batchQuery));
            cacheBlocks.addAll(dirtyBlocks);
        }
        System.out.printf("=======返回缓存:%d========",cacheBlocks.size());
        return cacheBlocks;
    }
}

package org.template.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisConfig {


    @Bean(name = "updateBlockScript")
    public RedisScript<Long> updateBlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/update_block.lua")));
        script.setResultType(Long.class);   // Lua 返回 1/0
        return script;
    }

    /** 写路径冷状态兜底：原子续期 TTL / 补 __creating__ 占位（见方案书 3.1.2） */
    @Bean(name="ensureCreatingScript")
    public RedisScript<Long> ensureCreatingScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/ensure_creating.lua")));
        script.setResultType(Long.class);
        return script;
    }
}

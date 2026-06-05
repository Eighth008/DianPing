package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.service.Lock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RedisLockImpl implements Lock {
    String name;
    StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.fastUUID().toString(true);
    private static final DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();

    static {
        redisScript.setLocation(new ClassPathResource("unlock.lua"));
        redisScript.setResultType(Long.class);
    }

    public RedisLockImpl(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean lock(Long time) {
        String key = KEY_PREFIX + name;
        String id = ID_PREFIX + Thread.currentThread().getId();
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, id, time, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(lock);
    }

    @Override
    public void unlock() {
        String key = KEY_PREFIX + name;
        String id = ID_PREFIX + Thread.currentThread().getId();
        stringRedisTemplate.execute(redisScript, Collections.singletonList(key), id);
    }
}

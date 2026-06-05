package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class RedisCacheUtil {
    StringRedisTemplate stringRedisTemplate;

    public RedisCacheUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public <E, T> E queryWithPassThrough(String keyPref, T id, Class<E> eClass, Function<T, E> function) {
        String key = keyPref + id;
        String ansStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(ansStr)) {
            return JSONUtil.toBean(ansStr, eClass);
        }
        if (ansStr != null && ansStr.isEmpty()) {
            return null;
        }
        E ans = function.apply(id);
        if (ans == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(ans), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return ans;
    }
}

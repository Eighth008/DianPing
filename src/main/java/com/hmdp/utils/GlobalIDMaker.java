package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class GlobalIDMaker {
    private static final long TIME_STAMP = 1160697600;
    private static final int COUNT_SIZE = 32;

    StringRedisTemplate stringRedisTemplate;

    public GlobalIDMaker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long makeId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowEpochSecond - TIME_STAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("icy:" + keyPrefix + ":" + date);

        return (timeStamp << COUNT_SIZE) | increment;
    }
}

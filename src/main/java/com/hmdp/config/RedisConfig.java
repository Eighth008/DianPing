package com.hmdp.config;

import io.lettuce.core.RedisClient;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @Value("${redisson.address}")
    String redisAddress;

    @Bean
    RedissonClient redisClient(){
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress);
        return Redisson.create(config);
    }
}

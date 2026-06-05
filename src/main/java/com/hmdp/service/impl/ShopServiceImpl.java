package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.LogicTimeEntity;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisCacheUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedisCacheUtil redisCacheUtil;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryShopById(Long id) {
        //Shop shop = queryWithPassThrough(id);
        //Shop shop = queryWithMutex(id);
        Shop shop = redisCacheUtil.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById);
        if (shop == null) {
            return Result.fail("商户不存在！");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        updateById(shop);
        Long id = shop.getId();
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        Integer from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        Integer to = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(new Point(x, y)),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(to)
        );
        if (search == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();

        if(content.size()<=from){
            return Result.ok(Collections.emptyList());
        }
        content = content.stream().skip(from).toList();

        List<Long> ids = new ArrayList<>(content.size());
        Map<String,Distance> distanceMap = new HashMap<>(content.size());
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : content) {
            String id = result.getContent().getName();
            ids.add(Long.valueOf(id));

            distanceMap.put(id,result.getDistance());
        }

        String idsStr = StrUtil.join(",", ids);
        List<Shop> shopList = query().in("id", ids).last("order by field(id," + idsStr + ")").list();

        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shopList);
    }

/*
    private Shop queryWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopStr)) {
            return JSONUtil.toBean(shopStr, Shop.class);
        }
        if (shopStr != null && shopStr.isEmpty()) {
            return null;
        }
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
*/

/*
    private Shop queryWithMutex(Long id) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            String key = RedisConstants.CACHE_SHOP_KEY + id;
            String shopStr = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopStr)) {
                return JSONUtil.toBean(shopStr, Shop.class);
            }
            if (shopStr != null && shopStr.isEmpty()) {
                return null;
            }
            if (!getLock(lockKey)) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            shopStr = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopStr)) {
                return JSONUtil.toBean(shopStr, Shop.class);
            }
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return shop;
    }
*/

    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    private boolean getLock(String lockKey) {
        Boolean lockBoolean = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        boolean lock = BooleanUtil.isTrue(lockBoolean);
        return lock;
    }

    private Shop queryWithLogicTime(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String lock = RedisConstants.LOCK_SHOP_KEY + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(shopStr)) {
            return null;
        }

        LogicTimeEntity logicTimeEntity = JSONUtil.toBean(shopStr, LogicTimeEntity.class);
        JSONObject data = (JSONObject) logicTimeEntity.getData();

        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = logicTimeEntity.getTime();

        // 如果未过期,直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }

        // 已过期,尝试获取锁进行缓存重建
        if (getLock(lock)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                        try {
                            updateLoginTimeRedisCache(id, 20L);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            unlock(lock);
                        }
                    }
            );
        }
        // 返回旧数据
        return shop;
    }

    public void updateLoginTimeRedisCache(Long id, Long addTime) throws InterruptedException {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        Thread.sleep(200);
        Shop shop = getById(id);
        LogicTimeEntity logicTimeEntity = new LogicTimeEntity();
        logicTimeEntity.setData(shop);
        logicTimeEntity.setTime(LocalDateTime.now().plusSeconds(addTime));
        String redisData = JSONUtil.toJsonStr(logicTimeEntity);
        stringRedisTemplate.opsForValue().set(key, redisData);
    }
}

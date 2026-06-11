package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.GlobalIDMaker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    ISeckillVoucherService iSeckillVoucherService;

    @Resource
    com.hmdp.service.IVoucherService iVoucherService;

    @Resource
    GlobalIDMaker globalIDMaker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    private static final String KEY_PREFIX = "order";

    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    private static final ExecutorService SECKILL_VOUCHER_EXECUTOR = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;

    static {
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setLocation(new ClassPathResource("seckillVoucher.lua"));
        REDIS_SCRIPT.setResultType(long.class);
    }

    @PostConstruct
    void init() {
        SECKILL_VOUCHER_EXECUTOR.submit(() -> {
            String queueName = "stream.orders";
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> entry = list.get(0);
                    Map<Object, Object> map = entry.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);
                    handlerVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entry.getId());
                } catch (Exception e) {
                    log.error("处理订单异常！", e);
                    handlerPendingList();
                }
            }
        });
    }

    private void handlerPendingList() {
        String queueName = "stream.orders";

        while (true) {
            try {
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                if (list == null || list.isEmpty()) {
                    break;
                }
                MapRecord<String, Object, Object> entry = list.get(0);
                Map<Object, Object> map = entry.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);
                handlerVoucherOrder(voucherOrder);
                stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entry.getId());
            } catch (Exception e) {
                log.error("处理pending订单异常！", e);
            }
        }
    }

    /*
    @PostConstruct
    void init() {
        SECKILL_VOUCHER_EXECUTOR.submit(() -> {
            while (true) {
                try {
                    VoucherOrder voucherOrder = blockingQueue.take();
                    handlerVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
    */
    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        try {
            if (!lock.tryLock()) {
                log.info("不许重复下单！");
            }
            proxy.lockToBuyVoucher(voucherOrder);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long id = globalIDMaker.makeId(KEY_PREFIX);
        Long execute = stringRedisTemplate.execute(
                REDIS_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(id)
        );
        if (execute != 0) {
            return (execute == 1 ? Result.fail("库存不足！") : Result.fail("不可重复购买！"));
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(id);
    }

    /*
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long execute = stringRedisTemplate.execute(
                REDIS_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        if (execute != 0) {
            return (execute == 1 ? Result.fail("库存不足！") : Result.fail("不可重复购买！"));
        }

        long id = globalIDMaker.makeId(KEY_PREFIX);
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(id);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        blockingQueue.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(id);
    }
    */
    /*
    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //log.debug(voucher.toString());
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();

        if (beginTime.isAfter(now)) {
            return Result.fail("秒杀时间还未开始！");
        }

        if (endTime.isBefore(now)) {
            return Result.fail("秒杀时间已经结束！");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("秒杀券库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        //RedisLockImpl lock = new RedisLockImpl("order:"+userId,stringRedisTemplate);

        RLock lock = redissonClient.getLock("lock:order:" + userId);
        try {
            if(!lock.tryLock()){
                return Result.fail("不可重复购买！");
            }
            IVoucherOrderService proxy= (IVoucherOrderService) AopContext.currentProxy();
            return proxy.lockToBuyVoucher(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

    }
    */
    @Transactional
    public void lockToBuyVoucher(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        if (count > 0) {
            log.error("用户已经购买过了！");
        }

        boolean isUpdate = iSeckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId).ge("stock", 0).update();
        if (!isUpdate) {
            log.error("秒杀券库存不足！");
        }

        save(voucherOrder);
    }

    @Override
    public Result voucher(Long voucherId) {
        // 1. 查询优惠券信息
        com.hmdp.entity.Voucher voucher = iVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在！");
        }
    
        // 2. 判断是否为普通券（type=0为普通券，type=1为秒杀券）
        if (voucher.getType() != 0) {
            return Result.fail("该优惠券不是普通券！");
        }
    
        // 3. 判断优惠券是否上架
        if (voucher.getStatus() != 1) {
            return Result.fail("优惠券未上架或已下架！");
        }
    
        // 4. 获取用户ID
        Long userId = UserHolder.getUser().getId();
    
        // 5. 检查用户是否已经购买过该优惠券
        long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过该优惠券！");
        }
    
        // 6. 创建订单
        long orderId = globalIDMaker.makeId(KEY_PREFIX);
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setPayType(1); // 默认余额支付
        voucherOrder.setStatus(1); // 未支付状态
    
        // 7. 保存订单
        boolean isSaved = save(voucherOrder);
        if (!isSaved) {
            return Result.fail("下单失败！");
        }
    
        // 8. 返回订单ID
        return Result.ok(orderId);
    }
    /*
    @Trans actional
    public Result lockToBuyVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过了！");
        }
        boolean isUpdate = iSeckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId).ge("stock", 0).update();

        if (!isUpdate) {
            Result.fail("秒杀券库存不足！");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long id = globalIDMaker.makeId(KEY_PREFIX);
        voucherOrder.setId(id);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);

        save(voucherOrder);
        return Result.ok(id);
    }
    */
}

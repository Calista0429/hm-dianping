package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private final DefaultRedisScript<Long> SECKILL_SCRIPT;
    {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024 * 1024);

    private final static ExecutorService SECKILL_ORDERS_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init(){
        SECKILL_ORDERS_EXECUTOR.submit(new VoucherOrderHandler());

    }
    private class VoucherOrderHandler implements Runnable {


        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder voucherOrder = orderQueue.take();
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }

            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //通过订单获取用户id
        Long userId = voucherOrder.getUserId();
        //基于Redisson获取锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //获取锁失败
            log.error("一个人只允许下一单");
            return;
        }
        try{
            //获取代理对象
           proxy.createVoucher(voucherOrder);
        }finally {
            //删除锁
            lock.unlock();
        }

    }

    private IVoucherOrderService proxy;
    @Override
    public Result seckillsave(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        // 1. 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 2. 判断返回值是否为0
        int r = result.intValue();
        if (r != 0) {
            // 2.1 为1，则库存不足
            // 2.2 为2，则重复下单
            return Result.fail(r == 1 ? "库存不足": "重复下单");
        }

        // 3. 有购买资格，将订单添加到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 3.1 创建订单ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 3.2设置用户id
        voucherOrder.setUserId(userId);
        // 3.3设置代金券id
        voucherOrder.setVoucherId(voucherId);
        // 3.4 添加到阻塞队列
        orderQueue.add(voucherOrder);
        // 3.5获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 4. 实现下单
        return Result.ok();
    }
//    public Result seckillsave(Long voucherId) {
//        //查询秒杀券id
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否还未开始
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        if (beginTime.isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀还未开始");
//        }
//        //判断秒杀是否已经结束
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//        if (endTime.isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//
//        //判断库存是否还有
//        Integer stock = seckillVoucher.getStock();
//        if (stock < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        //添加悲观锁
//        // 如果字符常量池中已经包含一个等于此String对象的字符串,则返回常量池中字符串的引用
//        // 对于任意两个字符串 s 和 t，当且仅当 s.equals(t) 为 true 时，s.intern() == t.intern() 才为 true
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucher(voucherId);
////        }
//
//        //基于Redis实现集群之间互斥锁
/// /        SimpleRedisLock lock = new SimpleRedisLock("order", stringRedisTemplate);
/// /        boolean isLock = lock.tryLock(3);
//
//        //基于Redisson获取锁对象
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            //获取锁失败
//            return Result.fail("一个人只允许下一单");
//        }
//        try{
//            //获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucher(voucherId);
//
//        }finally {
//            //删除锁
//            lock.unlock();
//        }
    @Transactional
    public void createVoucher(VoucherOrder voucherOrder) {
        //实现一人一单
        Long userId = voucherOrder.getUserId();
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("一人只能下一单");
            return;
        }
        //库存减1，写到数据库中
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        //保存订单
        save(voucherOrder);
    }

}


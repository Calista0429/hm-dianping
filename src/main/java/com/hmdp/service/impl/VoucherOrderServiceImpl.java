package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillsave(Long voucherId) {
        //查询秒杀券id
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否还未开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还未开始");
        }
        //判断秒杀是否已经结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        //判断库存是否还有
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();

        //添加悲观锁
        // 如果字符常量池中已经包含一个等于此String对象的字符串,则返回常量池中字符串的引用
        // 对于任意两个字符串 s 和 t，当且仅当 s.equals(t) 为 true 时，s.intern() == t.intern() 才为 true
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        }

    }

    @Transactional
    public Result createVoucher(Long voucherId) {
        //实现一人一单
        Long userId = UserHolder.getUser().getId();
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("一人只能下一单");
        }
        //库存减1，写到数据库中
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //创建订单ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //设置用户id

        voucherOrder.setUserId(userId);
        //设置代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        //返回订单ID
        return Result.ok(orderId);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.ibatis.jdbc.Null;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {

        Shop shop = queryByIdWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);


    }
    public Shop queryByIdWithMutex(Long id) {
        //从redis获取店铺缓存信息
        String shopjson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopjson)) {
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return shop;
        }
        //因为 isNotBlank 会判断是否为 null, ""，如果不是null, 那么就是“”。
        if (shopjson != null) {
            return null;
        }
        Shop shop = null;
        //获取互斥锁key
        String key = LOCK_SHOP_KEY + id;
        try {
            //判断是否获取成功
            boolean tryLock = tryLock(key);

            if (!tryLock) {
                //失败，休眠重新查缓存
                Thread.sleep(50);
                return queryByIdWithMutex(id);
            }

            //再次查询缓存，查看是否有其他线程已经添加
            shopjson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(shopjson)) {
                shop = JSONUtil.toBean(shopjson, Shop.class);
                return shop;
            }

            //查数据库
            shop = getById(id);


            if (shop == null) {
                //添加空值到缓存
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return null;
            }

            //写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            unlock(key);
        }

        return shop;
    }

    //获取互斥锁
    private boolean tryLock(String key){
        //用setnx模拟互斥锁
        //给锁设置TTL，以免锁线程宕机
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock);

    }

    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //更新数据库
        updateById(shop);
        Long shopId = shop.getId();
        if (shopId == null) {
            Result.fail("店铺id不能为空");
        }
        //清理缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
        return Result.ok(shop);

    }
}

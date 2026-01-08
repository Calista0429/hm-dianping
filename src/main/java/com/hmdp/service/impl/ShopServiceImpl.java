package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

        String shopjson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopjson)) {
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return Result.ok(shop);
        }
        //因为 isNotBlank 会判断是否为 null, ""，如果不是null, 那么就是“”。
        if (shopjson != null) {
            return Result.fail("店铺不存在！");
        }
        Shop shop = getById(id);
        if (shop == null) {
            //添加空值到缓存
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在！");
        }

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
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

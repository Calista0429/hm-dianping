package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    //设置缓存穿透，缓存击穿（互斥锁）时的key
    public void set(String key, Object value, Long ttl, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), ttl, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long ttl, TimeUnit timeUnit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(ttl)));

        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryByIdWithLogical(String keyPrefix, ID id, Class <R> type , Long ttl, TimeUnit unit, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        //从redis中获取店铺信息
        String json = stringRedisTemplate.opsForValue().get(key);

        //缓存中不存在，说明是缓存击穿问题
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //存在，需要把json序列化成对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回信息
            return r;
        }
        //过期，获取锁，从数据库中查询并写到缓存
        String lockKey = LOCK_SHOP_KEY + id;
        boolean tryLock = tryLock(lockKey);
        if (tryLock) {
            //获取到锁之后，先判断缓存是否已经已经更新
            json = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(json, RedisData.class);
            expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                return r;
            }
            executorService.submit(() -> {
                try {
                    //查询数据库
                     R r1 = dbFallback.apply(id);
                    //写入缓存
                    this.setWithLogicalExpire(key, r1, ttl, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return r;
    }

    /**
     * 解决缓存穿透
     * @param keyPrefix
     * @param id
     * @param type
     * @param ttl
     * @param unit
     * @param dbFallback
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryByIdWithPassThrough(String keyPrefix, ID id, Class<R> type, Long ttl, TimeUnit unit, Function<ID, R> dbFallback) {
        //从redis获取店铺缓存信息
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //因为 isNotBlank 会判断是否为 null, ""，如果不是null, 那么就是“”。
        if (json != null) {
            return null;
        }

        //再次查询缓存，查看是否有其他线程已经添加
        json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        //查数据库
        R r = dbFallback.apply(id);

        if (r == null) {
            //添加空值到缓存
            this.set(key, "", ttl, unit);
            return null;
        }

        //写入redis
        this.set(key, JSONUtil.toJsonStr(r), ttl, unit);

        return r;
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

}

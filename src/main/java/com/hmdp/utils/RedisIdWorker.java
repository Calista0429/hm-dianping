package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final Long BEGIN_TIMESTAMP = 1767225600L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String prefixKey){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = epochSecond - BEGIN_TIMESTAMP;

        //生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + prefixKey + ":" + date);

        //拼接并返回

        return timestamp << 32 | count;
    }

    public static void main(String[] args) {
        LocalDateTime localTime = LocalDateTime.of(2026, 1, 1, 0, 0,0);
        long epochSecond = localTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);
    }
}

package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author zwf
 * @date 2024/3/15 10:32
 */

@Component
public class RedisIdWOrker {

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP=1710498938;

    /**
     * 序列号位数
     */
    private static final int COUNT_BITS=32;


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        //1、生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long timeStamp =now.toEpochSecond(ZoneOffset.UTC)-BEGIN_TIMESTAMP;

        //2、生成序列号
        //2.1、获取当天日期
        String date = now.format(DateTimeFormatter.ofPattern("yy:MM:dd"));
        //2.2、自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);

        //拼接并返回
        return timeStamp << COUNT_BITS | count;
    }
}

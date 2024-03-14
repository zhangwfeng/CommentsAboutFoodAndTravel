package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.constant.CacheConstant;
import com.hmdp.constant.ErrorMessage;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Author zwf
 * @date 2024/3/12 17:23
 */


@Slf4j
@Component
public class RedisCacheUtils_c {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisCacheUtils_c(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    //将数据写入缓存
    public void set(String key, Object value, Long TTL, TimeUnit timeUnit){
        stringRedisTemplate.opsForHash().putAll(key,BeanUtil.beanToMap(value));
        stringRedisTemplate.expire(key,TTL,timeUnit);
    }

    //将需设逻辑过期的数据写入缓存
    public void setWithLogicalExpire(String key, Object value,Long TTL, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(TTL)));
        stringRedisTemplate.opsForHash().putAll(key,BeanUtil.beanToMap(redisData,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName,filedValue)->{
                            if(filedValue==null){
                                return null;
                            }
                            else {
                                return filedValue.toString();
                            }
                        })));
    }

    //从缓存中取数据，具有防止缓存穿透功能
    public <R,ID> R queryWithPassThrough(
            String keyPrefix , ID id, R type, Function<ID,R> dbFallback ,Long TTL, TimeUnit timeUnit){
        //拼接Redis的key
        String key=keyPrefix+id;

        //查询缓存
        Map<Object, Object> jsonMap = stringRedisTemplate.opsForHash().entries(key);

        //查询缓存是否存在
        if(!jsonMap.isEmpty()){
            //存在
            R r = BeanUtil.fillBeanWithMap(jsonMap, type, false);
            return r;
        }

        if(jsonMap.containsKey("null")){
            //存在空值
            return null;
        }

        //不存在，查询数据库
        R r = dbFallback.apply(id);

        //数据库中不存在，返回null
        String rKey=CacheConstant.CACHE_SHOP_PREFIX+id;
        if(r==null){
            Map<String,Object> map=new HashMap<>();
            map.put("null",null);
            stringRedisTemplate.opsForHash().putAll(rKey,map);  //将空值写入redis，防止缓存击穿
            stringRedisTemplate.expire(rKey,CacheConstant.CACHE_INEXIST_SHOP_TTL,TimeUnit.MINUTES);
            return null;
        }

        //数据库中存在，将数据添加到缓存
        Map<String, Object> map = BeanUtil.beanToMap(r, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->{
                            if (fieldValue == null) {
                                return null;
                            } else {
                                return fieldValue.toString();
                            }
                        }));  //TODO 1、setIgnoreNullValue(true)忽略了null，为什么还要判null？2、我写的逻辑有无问题

        set(rKey,map,TTL+ RandomUtil.randomLong(10),timeUnit);  //给TTL添加随机值，防止缓存雪崩

        return r;
    }

    //从缓存中取数据，具有防止缓存击穿功能
    public <T,ID> T queryWithBreakdown(
            String keyPrefix , ID id, Function<ID,T> queryFromDb ,Long TTL, TimeUnit timeUnit){
        //1、查询redis缓存
        String key=keyPrefix+id;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);

        //2、判断缓存是否命中
        if(map.isEmpty()){
            //3未命中，直接返回null
            return null;
        }

        //4命中，把数据反序列化为对象
        RedisData<T> redisData = BeanUtil.fillBeanWithMap(map, new RedisData(), false);
        LocalDateTime expireTime = redisData.getExpireTime();  //TODO 读出的数据和redis中不一致
        T data = redisData.getData();//TODO 使用泛型？泛型擦除？

        //5、判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //6、未过期，返回数据
            return data;
        }

        //7、过期，尝试获取锁
        String lockKey=CacheConstant.LOCK_SHOP_PREFIX + id;
        boolean isLock = tryLock(lockKey);

        //8、判断是否获取锁
        if(isLock){
            //9、获取到锁，开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(()->{

                try {
                    saveData2Redis(keyPrefix,id,timeUnit.toSeconds(TTL),queryFromDb);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }

            });
        }


        //10、返回旧数据
        return data;

        //11、释放锁

    }

    //尝试获取锁
    private boolean tryLock(String key){
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock); //直接returnBoolean类型会进行拆箱，可能出现空指针异常
    }

    //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    //缓存预热，将数据写入Redis
    public  <T,ID> void saveData2Redis(String keyPrefix, ID id,Long expireSeconds,Function<ID,T> queryFormDb) throws InterruptedException {
        //1、从数据库查询数据
        T data = queryFormDb.apply(id);

        //模拟缓存延时
        Thread.sleep(200);

        //2、封装逻辑过期时间
        setWithLogicalExpire(keyPrefix+id,data,expireSeconds,TimeUnit.SECONDS);

    }
}

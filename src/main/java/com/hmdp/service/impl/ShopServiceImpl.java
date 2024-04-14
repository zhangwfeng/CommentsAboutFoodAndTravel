package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.hmdp.constant.CacheConstant;
import com.hmdp.constant.ErrorMessage;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisCacheUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedisCacheUtils redisCacheUtils;

    @Override
    public Result queryById(Long id) throws InterruptedException {

        //防止缓存穿透
        Shop shop = redisCacheUtils.queryWithPassThrough(CacheConstant.CACHE_SHOP_PREFIX, id, Shop.class, this::getById, CacheConstant.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //防止缓存击穿
        //Shop shop = redisCacheUtils.queryWithBreakdown(CacheConstant.CACHE_SHOP_PREFIX, id,Shop.class, this::getById, CacheConstant.CACHE_SHOP_TTL);

        if(shop==null){
            return Result.fail(ErrorMessage.QUERY_DATA_NOT_EXIST);
        }

        //返回数据
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail(ErrorMessage.SHOP_ID_NULL);
        }

        //更新数据库表
        updateById(shop);

        //删除缓存
        redisTemplate.delete(CacheConstant.CACHE_SHOP_PREFIX+id);

        return Result.ok();
    }

    @Override
    public Result warm(Long id) throws InterruptedException {
        //缓存预热，将热点数据写入Redis
        redisCacheUtils.saveData2Redis(CacheConstant.CACHE_SHOP_PREFIX,id,20L,this::getById);
        return Result.ok();
    }
}




//防止缓存穿透未封装
// //查询缓存
// Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(CacheConstant.CACHE_SHOP_PREFIX + id);
//
// //查询缓存是否存在
// if(!shopMap.isEmpty()){
//     //存在
//     Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
//     return Result.ok(shop);
// }
//
// if(shopMap.containsKey("null")){
//     //存在空值
//     return Result.fail(ErrorMessage.QUERY_DATA_NOT_EXIST);  //命中空值，防止缓存击穿
// }
//
// //不存在，查询数据库
// Shop shop = getById(id);
//
// //数据库中不存在，返回错误
// String shopKey=CacheConstant.CACHE_SHOP_PREFIX+id;
// if(shop==null){
//     Map<String,Object> map=new HashMap<>();
//     map.put("null",null);
//     redisTemplate.opsForHash().putAll(shopKey,map);  //将空值写入redis，防止缓存击穿
//     redisTemplate.expire(shopKey,CacheConstant.CACHE_INEXIST_SHOP_TTL,TimeUnit.MINUTES);
//     return Result.fail(ErrorMessage.QUERY_DATA_NOT_EXIST);
// }
//
// //数据库中存在，将数据添加到缓存
// Map<String, Object> map = BeanUtil.beanToMap(shop, new HashMap<>(),false,true);
// redisTemplate.opsForHash().putAll(shopKey,map);
// redisTemplate.expire(shopKey,CacheConstant.CACHE_SHOP_TTL+ RandomUtil.randomLong(10),TimeUnit.MINUTES);  //设置缓存超时时间，给TTL添加随机值，防止缓存雪崩

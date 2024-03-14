package com.hmdp.service.impl;

import com.hmdp.constant.CacheConstant;
import com.hmdp.constant.ErrorMessage;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Result shopList() {
        //查询缓存
        List<ShopType> list = (List<ShopType>) redisTemplate.opsForValue().get(CacheConstant.CACHE_SHOP_LIST_PREFIX);

        //缓存是否存在
        if(list!=null&&!list.isEmpty()){
            //存在
            return Result.ok(list);
        }

        //缓存不存在，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        //数据库是否存在
        if(shopTypeList==null||shopTypeList.isEmpty()){
            //不存在
            return Result.fail(ErrorMessage.QUERY_DATA_NOT_EXIST);
        }

        //将数据写入缓存
        redisTemplate.opsForValue().set(CacheConstant.CACHE_SHOP_LIST_PREFIX,shopTypeList);

        //返回数据
        return Result.ok(shopTypeList);
    }
}

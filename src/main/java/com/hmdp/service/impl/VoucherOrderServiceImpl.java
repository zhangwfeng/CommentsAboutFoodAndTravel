package com.hmdp.service.impl;

import com.hmdp.constant.CacheConstant;
import com.hmdp.constant.ErrorMessage;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWOrker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    private RedisIdWOrker redisIdWOrker;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public Result voucherOrder(Long voucherId) {
        //1、查询优惠券
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);

        //2、判断是否在秒杀活动区间
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            //秒杀未开始
            return Result.fail(ErrorMessage.KILL_NOT_BEGIN);
        }else if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            //秒杀已结束
            return Result.fail(ErrorMessage.KILL_HAS_END);
        }

        //3、判断库存是否充足
        if(voucher.getStock()<1){
            //库存不足
            return Result.fail(ErrorMessage.STOCK_NOT_ENOUGH);
        }

        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        RLock lock = redissonClient.getLock(CacheConstant.LOCK_ORDER_PREFIX + userId);

        //获取锁（防止一人并发重复下单）
        boolean isLock = lock.tryLock();

        //判断是否获取锁成功
        if(!isLock){
            //获取锁失败，返回错误
            return Result.fail(ErrorMessage.REPEAT_PURCHASE_ERROE);
        }
        try{
            //获取代理对象（事务）TODO 原理？
            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(userId,voucherId);
        }finally {
            lock.unlock();
        }

        // Long userId = UserHolder.getUser().getId();
        // //改成Redisson实现分布式锁
        // synchronized (userId.toString().intern()) {  //intern（）从常量池中获取对象，保证是同一个对象
        //     IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();  //拿到当前对象的代理对象
        //     return proxy.createVoucherOrder(userId,voucherId);
        // }
    }

    @Transactional
    public Result createVoucherOrder(Long userId,Long voucherId) {
        //判断当前用户是否已经抢购了该优惠券
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count>0){
            //当前用户已经抢购过该优惠券，不允许再抢
            return Result.fail(ErrorMessage.REPEAT_PURCHASE_ERROE);
        }

        //5、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1、订单id
        long orderId = redisIdWOrker.nextId("order");
        voucherOrder.setId(orderId);
        //5.2、用户id
        voucherOrder.setUserId(userId);
        //5.3、代金券id
        voucherOrder.setVoucherId(voucherId);

        //发送扣减库存消息
        rabbitTemplate.convertAndSend("orders.direct","deduct",voucherId);

        //发送保存订单消息
        rabbitTemplate.convertAndSend("orders.direct","save",voucherOrder);

        //返回订单id
        return Result.ok(orderId);
    }
}

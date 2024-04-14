package com.hmdp.listeners;

import com.hmdp.constant.ErrorMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author zwf
 * @date 2024/3/30 11:55
 */

@Component
@Slf4j
public class deductStockAndSaveOrders {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    private IVoucherOrderService iVoucherOrderService;

    //监听扣减库存队列
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "deduct.stock",durable = "true"),
            exchange = @Exchange(name = "orders.direct",type = ExchangeTypes.DIRECT),
            key="deduct"
    ))
    public void DeductStock(Long voucherId){
        //4、扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  //乐观锁，利用mysql的update语句的排他锁
                .update();
        if(!success){  //乐观锁
            //System.out.println("+:"+(++count));
            log.error(ErrorMessage.STOCK_NOT_ENOUGH);
        }
    }

    //监听保存订单信息队列
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "save.orders",durable = "true"),
            exchange = @Exchange(name = "orders.direct"),
            key = "save"
    ))
    public void SaveOrders(VoucherOrder voucherOrder){
        iVoucherOrderService.save(voucherOrder);
    }
}

package com.lantin.unitrade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.lantin.unitrade.constant.MQConstants;
import com.lantin.unitrade.constant.RedisConstants;
import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.OrderDetailDTO;
import com.lantin.unitrade.domain.dto.OrderFormDTO;
import com.lantin.unitrade.domain.dto.UserDTO;
import com.lantin.unitrade.domain.po.Cart;
import com.lantin.unitrade.domain.po.Order;
import com.lantin.unitrade.domain.po.OrderDetail;
import com.lantin.unitrade.enums.OrderStatus;
import com.lantin.unitrade.enums.PayStatus;
import com.lantin.unitrade.exception.BadRequestException;
import com.lantin.unitrade.mapper.OrderMapper;
import com.lantin.unitrade.service.*;
import com.lantin.unitrade.utils.RabbitMqHelper;
import com.lantin.unitrade.utils.RedisIdWorker;
import com.lantin.unitrade.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.lantin.unitrade.constant.MQConstants.*;
import static com.lantin.unitrade.constant.RedisConstants.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final IItemService itemService;
    private final ICartService cartService;
    private final IPayOrderService payOrderService;
    private final RabbitTemplate rabbitTemplate;
    private final IOrderDetailService orderDetailService;
    private final RedissonClient redissonClient;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitMqHelper rabbitMqHelper;
    // private final MessagePostProcessor userInfoPostProcessor;

    // 商品秒杀业务lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 利用静态代码块加载lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    /**
     * 下单
     * @param orderFormDTO
     * @return
     */
    @Override
    public Long placeOrder(OrderFormDTO orderFormDTO) {
        // 1. 获取当前用户id
        UserDTO user = UserHolder.getUser();
        // 2. 生成订单id（是否真正生成订单由lua脚本判断）
        long orderId = redisIdWorker.nextId(RedisConstants.ORDER_PREFIX);    // 生成全局唯一且递增的订单id
        // 3. 加分布式锁，确保判断库存和扣库存的原子性
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 由于要原子性地判断多个商品的库存，这里给订单服务整个加锁，操作redis比较快，影响应该不大
        RLock lock = redissonClient.getLock(LOCK_GEN_ORDER_KEY);
        boolean enough = true; // 所有商品库存都足够则为true
        try {
            boolean getLock = lock.tryLock(2, TimeUnit.SECONDS);    // 如果2秒内没获取到锁则返回false
            if (getLock) {
                // 获取锁成功，判断所有商品的库存是否充足
                for (OrderDetailDTO detailDTO : detailDTOS) {
                    // 判断redis中记录的商品库存是否充足
                    String stockKey = ITEM_STOCK_KEY + detailDTO.getItemId();
                    String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
                    if (StrUtil.isBlank(stockStr)) {
                        enough = false;
                        log.error("缓存中商品库存不存在！");
                        break;
                    }
                    int stock = Integer.parseInt(stockStr);
                    if (stock < detailDTO.getNum()) {   // 库存不够购买量
                        // 库存不足
                        enough = false;
                        log.error("商品库存不足！");
                        break;
                    }
                }
                // 如果所有商品库存都充足，则统一扣减redis库存
                if (enough) {
                    for (OrderDetailDTO detailDTO : detailDTOS) {
                        String stockKey = ITEM_STOCK_KEY + detailDTO.getItemId();
                        stringRedisTemplate.opsForValue().decrement(stockKey, detailDTO.getNum());
                    }
                }
                // 发送包含订单id及购物车列表地消息到MQ异步创建订单和扣减库存
                rabbitMqHelper.sendMessageWithConfirm(ORDER_DIRECT_EXCHANGE, ORDER_SUCCESS_ROUTINGKEY, orderFormDTO, message -> {
                    // 将用户信息和生成的订单id放入消息头
                    message.getMessageProperties().setHeader("user-info", user);
                    message.getMessageProperties().setHeader("order-id", orderId);
                    return message;
                }, 3); // 生产者最多重试三次
            } else {
                // 锁获取失败，处理超时逻辑
                log.error("下单获取锁超时！");
            }
        } catch (InterruptedException e) {
            // 线程在等待锁时被中断
            log.error("下单等待锁时被中断！");
        } finally {
            // 解锁
            if (lock.isLocked()) {
                lock.unlock();
            }
        }
        return orderId; // 将生成的订单id返回给前端
    }

    /**
     * 生成订单，下单成功后执行，从消息队列中取出消息异步处理
     * @param orderFormDTO
     * @param orderId 订单id是由全局id生成器生成的，所以直接传过来
     * @return
     */
    @Override
    @Transactional
    public Long createOrder(OrderFormDTO orderFormDTO, Long orderId) {
        // 1.订单数据
        Order order = new Order();
        order.setId(orderId);   // 订单id需要手动设置（用全局id生成器生成的）
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemService.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserHolder.getUser().getId());
        order.setStatus(OrderStatus.UNPAID.getValue()); // 设置订单状态为未支付
        // 1.6.将Order写入数据库order表中，这里做一次幂等性判断，防止重复下单
        if (checkOrderExists(orderId)) {
            log.warn("订单已存在，订单id：{}", orderId);
            return orderId;
        }
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        orderDetailService.saveBatch(details);

        // 3.基于RabbitMQ的异步通知清理购物车商品（删除购物车中本次购买的商品）
        // 消息内容是要删除的购物车商品ids，用户信息放到了消息头
        rabbitMqHelper.sendMessage(ORDER_TOPIC_EXCHANGE, CART_CLEAR_ROUTINGKEY, itemIds, message -> {
            message.getMessageProperties().setHeader("user-info", UserHolder.getUser());
            return message;
        });

        // 4.扣减库存（修改数据库商品表中的库存）
        try {
            itemService.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }

        log.info("用户已下单，准备发送延迟消息...");

        // 5. 发送延迟消息，一段时间后检测订单支付状态（检查是否支付超时）
        rabbitMqHelper.sendDelayMessage(DELAY_EXCHANGE, DELAY_ORDER_KEY, orderId, 10000); // 延迟时间为方便测试设成10秒

        return order.getId();
    }

    /**
     * 检查订单是否已经存在
     * @param orderId
     * @return
     */
    private boolean checkOrderExists(Long orderId) {
        int count = lambdaQuery()
                .eq(Order::getId, orderId)
                .count();
        return count > 0;
    }


    @Override
    @Transactional
    public void markOrderPaySuccess(Long orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PAID.getValue());
        order.setPayTime(LocalDateTime.now());
        updateById(order);
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }


    /**
     * 取消订单，恢复库存
     * @param orderId
     */
    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        // 1. 幂等性判断
        Order order = getById(orderId);
        // 如果业务单状态已经是已取消，直接结束
        if (order == null || OrderStatus.CANCEL.equalsValue(order.getStatus())) {
            return;
        }

        // 2. 标记业务订单状态为已取消
        lambdaUpdate()
                .set(Order::getStatus, OrderStatus.CANCEL.getValue())
                .set(Order::getCloseTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .update();

        // 3. 修改支付单状态为已关闭（支付超时或取消订单了）
        payOrderService.updateStatusByOrderId(orderId, PayStatus.TRADE_CLOSED.getValue());

        // 4. 恢复库存
        // 4.1 查询本条订单中的商品及数量
        List<OrderDetail> orderDetails = orderDetailService
                .query()
                .eq("order_id", orderId)
                .list();
        List<OrderDetailDTO> detailDTOS = BeanUtil.copyToList(orderDetails, OrderDetailDTO.class);

        try {
            // 4.2 恢复商品库存
            itemService.restoreStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("恢复库存出错！");
        }
    }


}

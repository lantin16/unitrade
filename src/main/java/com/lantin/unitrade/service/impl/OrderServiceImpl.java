package com.lantin.unitrade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.OrderDetailDTO;
import com.lantin.unitrade.domain.dto.OrderFormDTO;
import com.lantin.unitrade.domain.po.Order;
import com.lantin.unitrade.domain.po.OrderDetail;
import com.lantin.unitrade.enums.OrderStatus;
import com.lantin.unitrade.exception.BadRequestException;
import com.lantin.unitrade.mapper.OrderMapper;
import com.lantin.unitrade.service.IItemService;
import com.lantin.unitrade.service.IOrderDetailService;
import com.lantin.unitrade.service.IOrderService;
import com.lantin.unitrade.service.IPayOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final IItemService itemService;
    private final CartClient cartClient;
    private final IPayOrderService payOrderService;
    private final RabbitTemplate rabbitTemplate;
    private final IOrderDetailService orderDetailService;

    @Override
    @Transactional    // 标记分布式事务的入口方法
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
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
        order.setUserId(UserHolder.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.清理购物车商品
        cartClient.deleteCartItemByIds(itemIds);

        // 4.扣减库存
        try {
            itemClient.deductItemStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }

        log.info("用户已下单，准备发送延迟消息...");

        // 5. 发送延迟消息，检测订单支付状态
        rabbitTemplate.convertAndSend(
                MQConstants.DELAY_EXCHANGE_NAME,
                MQConstants.DELAY_ORDER_KEY,
                order.getId(),
                message -> {    // 利用消息后处理器在消息头中添加延迟消息属性
                    message.getMessageProperties().setDelay(10000); // 延迟时间为方便测试设成10秒
                    return message;
                });
        return order.getId();
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
     * 取消订单
     * @param orderId
     */
    @Override
    @GlobalTransactional
    public void cancelOrder(Long orderId) {
        // 1. 标记订单状态为已关闭
        lambdaUpdate()
                .set(Order::getStatus, 5)
                .set(Order::getCloseTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .update();

        // 2. 修改支付单状态为已取消
        payClient.updatePayStatusByBizOrderNo(orderId, 5);

        // 2. 恢复库存
        // 2.1 查询本条订单中的商品及数量
        List<OrderDetail> orderDetails = orderDetailService
                .query()
                .eq("order_id", orderId)
                .list();
        List<OrderDetailDTO> detailDTOS = BeanUtil.copyToList(orderDetails, OrderDetailDTO.class);

        try {
            // 2.2 恢复商品库存
            itemClient.restoreStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("恢复库存出错！");
        }
    }
}

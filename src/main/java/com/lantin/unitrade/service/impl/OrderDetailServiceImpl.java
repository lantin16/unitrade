package com.lantin.unitrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lantin.unitrade.domain.po.OrderDetail;
import com.lantin.unitrade.mapper.OrderDetailMapper;
import com.lantin.unitrade.service.IOrderDetailService;
import org.springframework.stereotype.Service;


@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail> implements IOrderDetailService {

}

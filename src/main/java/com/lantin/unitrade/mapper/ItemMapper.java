package com.lantin.unitrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantin.unitrade.domain.dto.OrderDetailDTO;
import com.lantin.unitrade.domain.po.Item;
import org.apache.ibatis.annotations.Update;


public interface ItemMapper extends BaseMapper<Item> {

    @Update("UPDATE item SET stock = stock - #{num} WHERE id = #{itemId}")
    void updateStock(OrderDetailDTO orderDetail);
}

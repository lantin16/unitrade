package com.lantin.unitrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.OrderDetailDTO;
import com.lantin.unitrade.domain.po.Item;


import java.util.Collection;
import java.util.List;


public interface IItemService extends IService<Item> {

    void deductStock(List<OrderDetailDTO> items);

    List<ItemDTO> queryItemByIds(Collection<Long> ids);

    void restoreStock(List<OrderDetailDTO> items);
}

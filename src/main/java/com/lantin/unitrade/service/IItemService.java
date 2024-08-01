package com.lantin.unitrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.OrderDetailDTO;
import com.lantin.unitrade.domain.dto.Result;
import com.lantin.unitrade.domain.po.Item;
import org.springframework.transaction.annotation.Transactional;


import java.util.Collection;
import java.util.List;


public interface IItemService extends IService<Item> {

    void deductStock(List<OrderDetailDTO> items);

    List<ItemDTO> queryItemByIds(Collection<Long> ids);

    ItemDTO queryItemById(Long id);

    void restoreStock(List<OrderDetailDTO> items);

    Result update(Item item);

    void saveItem(ItemDTO item);
}

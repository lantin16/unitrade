package com.lantin.unitrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lantin.unitrade.domain.dto.CartFormDTO;
import com.lantin.unitrade.domain.po.Cart;
import com.lantin.unitrade.domain.vo.CartVO;
import java.util.Collection;
import java.util.List;


public interface ICartService extends IService<Cart> {

    void addItem2Cart(CartFormDTO cartFormDTO);

    List<CartVO> queryMyCarts();

    void removeByItemIds(Collection<Long> itemIds);
}

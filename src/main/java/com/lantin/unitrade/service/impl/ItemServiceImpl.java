package com.lantin.unitrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.OrderDetailDTO;
import com.lantin.unitrade.domain.po.Item;
import com.lantin.unitrade.exception.BizIllegalException;
import com.lantin.unitrade.mapper.ItemMapper;
import com.lantin.unitrade.service.IItemService;
import com.lantin.unitrade.utils.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    @Override
    @Transactional
    public void deductStock(List<OrderDetailDTO> items) {
        String sqlStatement = "com.hmall.item.mapper.ItemMapper.updateStock";
        boolean r = false;
        try {
            r = executeBatch(items, (sqlSession, entity) -> sqlSession.update(sqlStatement, entity));
        } catch (Exception e) {
            throw new BizIllegalException("更新库存异常，可能是库存不足!", e);
        }
        if (!r) {
            throw new BizIllegalException("库存不足！");
        }
    }

    @Override
    @Transactional
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
    }

    @Override
    @Transactional
    public void restoreStock(List<OrderDetailDTO> items) {
        for (OrderDetailDTO orderDetail : items) {
            // 根据商品id查询商品
            Item item = lambdaQuery()
                    .eq(Item::getId, orderDetail.getItemId())
                    .one();
            // 还原库存
            lambdaUpdate()
                    .set(Item::getStock, item.getStock() + orderDetail.getNum())    // 现在的库存 + 购买数量
                    .eq(Item::getId, orderDetail.getItemId())
                    .update();
        }
    }
}

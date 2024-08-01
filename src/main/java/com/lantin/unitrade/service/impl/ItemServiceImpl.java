package com.lantin.unitrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lantin.unitrade.constant.MessageConstants;
import com.lantin.unitrade.constant.RedisConstants;
import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.OrderDetailDTO;
import com.lantin.unitrade.domain.dto.Result;
import com.lantin.unitrade.domain.po.Item;
import com.lantin.unitrade.exception.BizIllegalException;
import com.lantin.unitrade.mapper.ItemMapper;
import com.lantin.unitrade.service.IItemService;
import com.lantin.unitrade.utils.BeanUtils;
import com.lantin.unitrade.utils.CacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.lantin.unitrade.constant.RedisConstants.ITEM_STOCK_KEY;


@Service
@RequiredArgsConstructor
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private final CacheClient cacheClient;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 上架商品
     * @param item
     */
    @Override
    public void saveItem(ItemDTO item) {
        // 1. 保存到数据库商品表
        save(BeanUtils.copyBean(item, Item.class));
        // 2. 添加商品库存缓存到redis，这里就不设置过期时间了（后续可以定期清理stock为0的商品缓存）
        stringRedisTemplate.opsForValue().set(ITEM_STOCK_KEY + item.getId(), String.valueOf(item.getStock()));
    }

    /**
     * 扣减库存
     * 下单成功后，扣减库存
     * @param items
     */
    @Override
    @Transactional
    public void deductStock(List<OrderDetailDTO> items) {
        String sqlStatement = "com.lantin.unitrade.mapper.ItemMapper.updateStock";  // 这里的updateStock是ItemMapper.xml中的id
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

    /**
     * 根据商品ids批量查询商品
     *
     * @param ids
     * @return
     */
    @Override
    @Transactional
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        List<ItemDTO> itemDTOS = new ArrayList<>();
        for (Long id : ids) {
            // 根据商品id查询商品
            Item item = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_ITEM_KEY, id, Item.class,
                    RedisConstants.LOCK_ITEM_KEY, this::getById, RedisConstants.CACHE_ITEM_TTL, TimeUnit.MINUTES);
            itemDTOS.add(BeanUtils.copyProperties(item, ItemDTO.class));
        }
        
        return itemDTOS;

        // TODO 先查询缓存，缓存没有再查询数据库，多级缓存实现？
        // 下面这种是直接批量查数据库
        // return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
    }


    /**
     * 根据商品id查询商品
     * 商品是热点信息，利用逻辑过期解决缓存击穿问题
     *
     * @param id
     * @return
     */
    @Override
    @Transactional
    public ItemDTO queryItemById(Long id) {
        Item item = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_ITEM_KEY, id, Item.class,
                RedisConstants.LOCK_ITEM_KEY, this::getById, RedisConstants.CACHE_ITEM_TTL, TimeUnit.MINUTES);

        // TODO 先查询缓存，缓存没有再查询数据库，多级缓存实现？
        return BeanUtils.copyProperties(item, ItemDTO.class);
    }

    /**
     * 恢复库存
     * 可能是由于超时未支付、取消订单等原因
     * @param items
     */
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


    /**
     * 更新商品信息
     * 先更新数据库，再删除redis缓存
     *
     * @param item
     */
    @Override
    @Transactional  // 保证数据库和redis双写操作的一致性
    public Result update(Item item) {
        Long id = item.getId();
        if (id == null) {
            return Result.fail(MessageConstants.SHOP_NOT_EXIST);
        }

        // 1. 先更新数据库
        updateById(item);

        // 2. 再删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_ITEM_KEY + id);

        return Result.ok();
    }


}

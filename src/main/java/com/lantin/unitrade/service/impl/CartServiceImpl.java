package com.lantin.unitrade.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lantin.unitrade.config.CartProperties;
import com.lantin.unitrade.domain.dto.CartFormDTO;
import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.po.Cart;
import com.lantin.unitrade.domain.vo.CartVO;
import com.lantin.unitrade.exception.BizIllegalException;
import com.lantin.unitrade.mapper.CartMapper;
import com.lantin.unitrade.service.ICartService;
import com.lantin.unitrade.service.IItemService;
import com.lantin.unitrade.utils.BeanUtils;
import com.lantin.unitrade.utils.CollUtils;
import com.lantin.unitrade.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    // private final RestTemplate restTemplate;    // 用final修饰就变成必须要初始化的成员变量，再加上@RequiredArgsConstructor注解就可以用lombok自动生成其构造函数完成依赖注入
    // private final DiscoveryClient discoveryClient;

    private final IItemService  itemService;

    private final CartProperties cartProperties;

    @Override
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser();

        // 2.判断是否已经存在
        if(checkItemExists(cartFormDTO.getItemId(), userId)){
            // 2.1.存在，则更新数量
            baseMapper.updateNum(cartFormDTO.getItemId(), userId);
            return;
        }
        // 2.2.不存在，判断是否超过购物车数量
        checkCartsFull(userId);

        // 3.新增购物车条目
        // 3.1.转换PO
        Cart cart = BeanUtils.copyBean(cartFormDTO, Cart.class);
        // 3.2.保存当前用户
        cart.setUserId(userId);
        // 3.3.保存到数据库
        save(cart);
    }

    @Override
    public List<CartVO> queryMyCarts() {
        // 1.查询我的购物车列表
        List<Cart> carts = lambdaQuery().eq(Cart::getUserId, UserHolder.getUser()).list();
        if (CollUtils.isEmpty(carts)) {
            return CollUtils.emptyList();
        }

        // 2.转换VO
        List<CartVO> vos = BeanUtils.copyList(carts, CartVO.class);

        // 3.处理VO中的商品信息
        handleCartItems(vos);

        // 4.返回
        return vos;
    }

    /**
     * 利用nacos注册中心通过服务名称动态地获取服务的实例，然后发起远程调用实现跨服务获取商品最新信息
     * @param vos
     */
    private void handleCartItems(List<CartVO> vos) {
        // 1.获取商品id
        Set<Long> itemIds = vos.stream().map(CartVO::getItemId).collect(Collectors.toSet());
        // 2.查询商品（这样写太麻烦了，用OpenFeign一行代码搞定）
        /*// 2.1 根据服务名称获取服务的实例列表
        List<ServiceInstance> instances = discoveryClient.getInstances("item-service");
        if (CollUtil.isEmpty(instances)) {  // 健壮性判断
            return;
        }
        // 2.2 手写负载均衡算法，从实例列表中挑选一个实例
        ServiceInstance instance = instances.get(RandomUtil.randomInt(instances.size()));   // 随机挑选
        // 2.3 利用RestTemplate发起http请求。得到http的响应
        ResponseEntity<List<ItemDTO>> response = restTemplate.exchange(
                instance.getUri() + "/items?ids={ids}", // 具体请求的服务地址从注册中心动态获取
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ItemDTO>>() {
                }, // 泛型在字节码文件中会被擦除，但是可以利用这种方式传递泛型
                Map.of("ids", CollUtil.join(itemIds, ","))  // ids占位符用id逗号拼接替换
        );

        // 2.4 解析响应
        if (!response.getStatusCode().is2xxSuccessful()) {  // 响应状态码不是以2xx代表请求失败
            // 查询失败，直接结束
            return;
        }
        List<ItemDTO> items = response.getBody();*/

        // 2.查询商品
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (CollUtils.isEmpty(items)) {
            return;
        }
        // 3.转为 id 到 item的map
        Map<Long, ItemDTO> itemMap = items.stream().collect(Collectors.toMap(ItemDTO::getId, Function.identity()));
        // 4.写入vo
        for (CartVO v : vos) {
            ItemDTO item = itemMap.get(v.getItemId());
            if (item == null) {
                continue;
            }
            v.setNewPrice(item.getPrice());
            v.setStatus(item.getStatus());
            v.setStock(item.getStock());
        }
    }

    @Override
    @Transactional
    public void removeByItemIds(Collection<Long> itemIds) {
        // 1.构建删除条件，userId和itemId
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, UserHolder.getUser())
                .in(Cart::getItemId, itemIds);
        // 2.删除
        remove(queryWrapper);
    }

    private void checkCartsFull(Long userId) {
        int count = lambdaQuery().eq(Cart::getUserId, userId).count();
        if (count >= cartProperties.getMaxItems()) {
            throw new BizIllegalException(StrUtil.format("用户购物车课程不能超过{}", cartProperties.getMaxItems()));
        }
    }

    private boolean checkItemExists(Long itemId, Long userId) {
        int count = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId)
                .count();
        return count > 0;
    }
}

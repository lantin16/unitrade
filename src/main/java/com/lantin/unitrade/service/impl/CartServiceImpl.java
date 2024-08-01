package com.lantin.unitrade.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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
import com.lantin.unitrade.utils.CacheClient;
import com.lantin.unitrade.utils.CollUtils;
import com.lantin.unitrade.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.message.LoggerNameAwareMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lantin.unitrade.constant.RedisConstants.CACHE_CART_KEY;
import static com.lantin.unitrade.constant.RedisConstants.CACHE_CART_TTL;


@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    // private final RestTemplate restTemplate;    // 用final修饰就变成必须要初始化的成员变量，再加上@RequiredArgsConstructor注解就可以用lombok自动生成其构造函数完成依赖注入
    // private final DiscoveryClient discoveryClient;

    private final IItemService  itemService;
    private final CartProperties cartProperties;
    private final CacheClient cacheClient;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 添加商品进购物车
     * 直接写入数据库购物车表，并删除redis缓存（因为这也相当于更新）
     * @param cartFormDTO
     */
    @Override
    @Transactional  // 保证数据库和redis双写操作的一致性
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();

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

        // 4. 删除redis缓存（下次用户查询购物车时会从数据库查到最新的）
        stringRedisTemplate.delete(CACHE_CART_KEY + userId);
    }

    /**
     * 查询用户自己的购物车列表
     * 先查redis，再查数据库（redis缓存主要提高的是查询购物车时的速度）
     * @return
     */
    @Override
    public List<CartVO> queryMyCarts() {
        Long userId = UserHolder.getUser().getId();
        String key = CACHE_CART_KEY + userId;   // 购物车在redis中以人为单位，每个人一个键值对
        // 1. 先查redis缓存是否有购物车数据
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 如果有，直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toList(json, CartVO.class);
        }

        // 3. 如果没有，查询数据库
        List<Cart> carts = lambdaQuery().eq(Cart::getUserId, UserHolder.getUser().getId()).list();
        if (CollUtils.isEmpty(carts)) {
            // 数据库也没有，返回空集合
            return CollUtils.emptyList();
        }

        // 4. 转换VO
        List<CartVO> vos = BeanUtils.copyList(carts, CartVO.class);

        // 5. 处理VO中的商品信息（主要是设置最新的库存、状态、价格等字段）
        handleCartItems(vos);

        // 6. 写入redis缓存，以便下次直接返回
        cacheClient.set(key, vos, CACHE_CART_TTL, TimeUnit.MINUTES);

        // 7. 返回给前端购物车数据
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

        // 2.查询商品的最新信息（价格、库存等）
        List<ItemDTO> items = itemService.queryItemByIds(itemIds);
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
            // 将商品的最新信息写入购物车VO（方便和加入购物车时比较）
            v.setNewPrice(item.getPrice());
            v.setStatus(item.getStatus());
            v.setStock(item.getStock());
        }
    }

    /**
     * 将从购物车中删除某些商品
     * 同时删除redis缓存
     * @param itemIds
     */
    @Override
    @Transactional
    public void removeByItemIds(Collection<Long> itemIds) {
        Long userId = UserHolder.getUser().getId();
        // 1.构建删除条件，userId和itemId
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, userId)
                .in(Cart::getItemId, itemIds);
        // 2.删除数据库购物车表中相关条目
        remove(queryWrapper);
        // 3.删除redis缓存
        stringRedisTemplate.delete(CACHE_CART_KEY + userId);
    }

    /**
     * 检查购物车是否已满
     * @param userId
     */
    private void checkCartsFull(Long userId) {
        int count = lambdaQuery().eq(Cart::getUserId, userId).count();
        if (count >= cartProperties.getMaxItems()) {
            throw new BizIllegalException(StrUtil.format("用户购物车课程不能超过{}", cartProperties.getMaxItems()));
        }
    }

    /**
     * 检查购物车中是否已经存在该商品（查的是数据库）
     * @param itemId
     * @param userId
     * @return
     */
    private boolean checkItemExists(Long itemId, Long userId) {
        int count = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId)
                .count();
        return count > 0;
    }
}

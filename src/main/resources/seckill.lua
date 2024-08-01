-- 1. 参数列表
-- 商品id
local itemId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 订单id
local orderId = ARGV[3]

-- 2. 数据key
-- 库存key
local stockKey = 'seckill:stock:' .. itemId
-- 下单key
local orderKey = 'seckill:order:' .. itemId

-- 3. 脚本业务，lua脚本使得判断库存和扣库存成为一个原子操作，保证了并发情况下的数据一致性
-- 判断库存是否充足 get stockKey
-- 注意redis取出的值是字符串，因此要转成数字再比较
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足
    return 1
end

-- 扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)

-- 发送消息到队列中 xadd stream.orders * k1 v1 k2 v2 ...
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'itemId', itemId, 'id', orderId)

return 0
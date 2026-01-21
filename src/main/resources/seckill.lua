--获得优惠券ID
local voucherId = ARGV[1]

--获得用户ID
local userId = ARGV[2]

--优惠券key
local stockKey = "seckill:stock:" .. voucherId
--订单key
local orderKey = "seckill:order:" .. voucherId

local stock = tonumber(redis.call('get', stockKey))
-- 1. 判断库存是否充足或者库存是否存在
if (not stock or stock <= 0) then
    -- 1.1 不充足，返回1
    return 1
end

-- 2. 判断用户是否下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 2.1 下过单，返回2
    return 2
end

-- 3. 下单
-- 3.1 添加用户到集合
redis.call('sadd', orderKey, userId)
-- 3.2 库存减1
redis.call('incrby', stockKey, -1)
--创建和修改成功，返回0
return 0





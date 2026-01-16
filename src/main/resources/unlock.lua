--获取锁标识
local id = ARGV[1]
--判断线程标识和锁标识是否一致
if (redis.call('get', KEYS[1]) == id) then
    return redis.call('del', KEYS[1])
end
--不一致，返回0
return 0



local likedKey = KEYS[1]
local countKey = KEYS[2]
local userId = ARGV[1]
local action = ARGV[2]

if action == 'LIKE' then
    if redis.call('sismember', likedKey, userId) == 1 then
        return 0
    end
    redis.call('sadd', likedKey, userId)
    redis.call('incrby', countKey, 1)
    return 1
end

if action == 'UNLIKE' then
    if redis.call('sismember', likedKey, userId) == 0 then
        return 0
    end
    redis.call('srem', likedKey, userId)
    local count = tonumber(redis.call('get', countKey) or '0')
    if count > 0 then
        redis.call('incrby', countKey, -1)
    else
        redis.call('set', countKey, 0)
    end
    return 1
end

return -1

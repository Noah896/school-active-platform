local activityId = ARGV[1]
local userId = ARGV[2]
local nowEpoch = tonumber(ARGV[3])
local requestId = ARGV[4]

-- 2. key
local metaKey = 'activity:meta:' .. activityId
local stockKey = 'activity:stock:' .. activityId
local frozenKey = 'activity:frozen:' .. activityId
local usersKey = 'activity:register:users:' .. activityId
local userStateKey = 'activity:user:register:' .. activityId .. ':' .. userId

-- 3. 基础校验
if redis.call('exists', metaKey) == 0 then
    return 6
end

local status = tonumber(redis.call('hget', metaKey, 'status') or '-1')
if status ~= 2 and status ~= 5 then
    return 3
end

local registrationStart = tonumber(redis.call('hget', metaKey, 'registrationStartEpoch') or '0')
if registrationStart > 0 and nowEpoch < registrationStart then
    return 4
end

local registrationEnd = tonumber(redis.call('hget', metaKey, 'registrationEndEpoch') or '0')
if registrationEnd > 0 and nowEpoch > registrationEnd then
    return 5
end

if redis.call('sismember', usersKey, userId) == 1 then
    return 2
end

local userState = redis.call('hgetall', userStateKey)
if userState ~= nil and #userState > 0 then
    local stateMap = {}
    for i = 1, #userState, 2 do
        stateMap[userState[i]] = userState[i + 1]
    end
    if stateMap['status'] == 'PENDING_CONFIRM' or stateMap['status'] == 'SUCCESS' or stateMap['status'] == 'CANCEL_PENDING' then
        return 2
    end
end

local stock = tonumber(redis.call('get', stockKey) or '-1')
if stock <= 0 then
    return 1
end

-- 4. 原子扣减并冻结
redis.call('incrby', stockKey, -1)
redis.call('incrby', frozenKey, 1)
redis.call('sadd', usersKey, userId)
redis.call('hmset', userStateKey,
    'status', 'PENDING_CONFIRM',
    'requestId', requestId,
    'message', '报名申请已提交，等待主办方审核'
)
redis.call('expire', userStateKey, 43200)

return 0

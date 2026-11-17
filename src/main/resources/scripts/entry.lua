if #KEYS ~= 4 then
  return redis.error_reply('exactly 4 keys are required')
end

if #ARGV ~= 6 then
  return redis.error_reply('exactly 6 arguments are required')
end

local rateLimitMillis = tonumber(ARGV[1])
local requestTtlMillis = tonumber(ARGV[2])
local instagramId = ARGV[3]
local requestId = ARGV[4]
local eventId = ARGV[5]
local acceptedAt = ARGV[6]

if not rateLimitMillis or rateLimitMillis <= 0 then
  return redis.error_reply('rate limit must be a positive number')
end

if not requestTtlMillis or requestTtlMillis <= 0 then
  return redis.error_reply('request ttl must be a positive number')
end

if instagramId == '' or requestId == '' or eventId == '' or acceptedAt == '' then
  return redis.error_reply('entry fields are required')
end

local function keyType(key)
  local result = redis.call('TYPE', key)
  if type(result) == 'table' then
    return result.ok
  end
  return result
end

local requestsType = keyType(KEYS[1])
local counterType = keyType(KEYS[3])
local streamType = keyType(KEYS[4])

if requestsType ~= 'none' and requestsType ~= 'hash' then
  return redis.error_reply('invalid requests key type')
end

if counterType ~= 'none' and counterType ~= 'string' then
  return redis.error_reply('invalid counter key type')
end

if streamType ~= 'none' and streamType ~= 'stream' then
  return redis.error_reply('invalid outbox key type')
end

local issued = redis.call('HGET', KEYS[1], requestId)
if issued then
  return {1, tonumber(issued)}
end

if not redis.call('SET', KEYS[2], '1', 'PX', rateLimitMillis, 'NX') then
  return {2, 0}
end

local previousCounter = redis.call('GET', KEYS[3])
local rankResult = redis.pcall('INCR', KEYS[3])

if type(rankResult) == 'table' and rankResult.err then
  redis.call('DEL', KEYS[2])
  return redis.error_reply('counter increment failed: ' .. rankResult.err)
end

local rank = tonumber(rankResult)

local function restoreCounter()
  if previousCounter then
    redis.call('SET', KEYS[3], previousCounter)
  else
    redis.call('DEL', KEYS[3])
  end
end

local requestResult = redis.pcall('HSET', KEYS[1], requestId, rank)

if type(requestResult) == 'table' and requestResult.err then
  redis.call('DEL', KEYS[2])
  restoreCounter()
  return redis.error_reply('idempotency write failed: ' .. requestResult.err)
end

local outboxResult = redis.pcall(
  'XADD',
  KEYS[4],
  '*',
  'requestId', requestId,
  'instagramId', instagramId,
  'eventId', eventId,
  'rank', rank,
  'acceptedAt', acceptedAt
)

if type(outboxResult) == 'table' and outboxResult.err then
  redis.call('DEL', KEYS[2])
  redis.call('HDEL', KEYS[1], requestId)
  restoreCounter()
  return redis.error_reply('outbox write failed: ' .. outboxResult.err)
end

redis.pcall('PEXPIRE', KEYS[1], requestTtlMillis)
redis.pcall('PEXPIRE', KEYS[3], requestTtlMillis)

return {0, rank}

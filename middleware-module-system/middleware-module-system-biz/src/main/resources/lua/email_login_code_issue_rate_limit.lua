local function is_positive_integer(value)
  return type(value) == 'string' and string.match(value, '^[1-9][0-9]*$') ~= nil
end

local function is_canonical_counter(value)
  return type(value) == 'string'
      and (value == '0' or string.match(value, '^[1-9][0-9]*$') ~= nil)
end

if not is_positive_integer(ARGV[1])
    or not is_positive_integer(ARGV[2])
    or not is_positive_integer(ARGV[3]) then
  return -2
end

local cooldown = ARGV[1]
local window = ARGV[2]
local maximum = tonumber(ARGV[3])
if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
local counters = {KEYS[3], KEYS[4]}
local limit_exceeded = false
for _, key in ipairs(counters) do
  local value = redis.call('GET', key)
  if value then
    if not is_canonical_counter(value) or redis.call('PTTL', key) <= 0 then return -2 end
    if tonumber(value) >= maximum then limit_exceeded = true end
  end
end
if limit_exceeded then return -1 end
redis.call('SET', KEYS[1], '1', 'PX', cooldown)
redis.call('SET', KEYS[2], '1', 'PX', cooldown)
for _, key in ipairs(counters) do
  local count = redis.call('INCR', key)
  if count == 1 then redis.call('PEXPIRE', key, window) end
end
return 1

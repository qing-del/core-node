if #KEYS ~= 1 or #ARGV ~= 2
    or type(ARGV[1]) ~= 'string' or #ARGV[1] == 0
    or not string.match(ARGV[2], '^[1-5]$') then return -1 end

local verifier = redis.call('HGET', KEYS[1], 'verifier_hash')
if not verifier or verifier ~= ARGV[1] then return 0 end

local failed_attempts = redis.call('HGET', KEYS[1], 'failed_attempts')
if not failed_attempts or not string.match(failed_attempts, '^[0-4]$')
    or redis.call('PTTL', KEYS[1]) <= 0 then return -1 end

local next_attempt = tonumber(failed_attempts) + 1
if next_attempt >= tonumber(ARGV[2]) then
  redis.call('DEL', KEYS[1])
  return 2
end

redis.call('HSET', KEYS[1], 'failed_attempts', next_attempt)
return 1

if #KEYS ~= 1 or #ARGV ~= 1 or type(ARGV[1]) ~= 'string' or #ARGV[1] == 0 then return -1 end

local verifier = redis.call('HGET', KEYS[1], 'verifier_hash')
if not verifier or verifier ~= ARGV[1] then return 0 end

redis.call('DEL', KEYS[1])
return 1

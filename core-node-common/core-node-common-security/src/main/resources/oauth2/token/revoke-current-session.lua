if (#KEYS ~= 2 and #KEYS ~= 3) or #ARGV ~= 2 then return -1 end
if not string.match(ARGV[1], '^[1-9][0-9]*$') then return -1 end
if type(ARGV[2]) ~= 'string' then return -1 end

local expected = ARGV[2]
if expected ~= '' and (#expected ~= 43 or not string.match(expected, '^[A-Za-z0-9_-]+$')) then return -1 end
if #KEYS == 2 and expected ~= '' then return -1 end
if #KEYS == 3 and expected == '' then return -1 end

if expected ~= '' then
  if redis.call('HGET', KEYS[2], 'current_refresh_fingerprint') ~= expected then return 0 end
elseif redis.call('EXISTS', KEYS[2]) ~= 0 then
  return 0
end

redis.call('SET', KEYS[1], '1', 'PX', ARGV[1])
if expected ~= '' then redis.call('DEL', KEYS[2]) end
if #KEYS == 3 then redis.call('DEL', KEYS[3]) end
return 1

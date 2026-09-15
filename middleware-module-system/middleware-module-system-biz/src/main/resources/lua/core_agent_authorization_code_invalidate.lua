if #KEYS ~= 1 or #ARGV ~= 3 then return -1 end
if not string.match(ARGV[1], '^[1-9][0-9]*$') then return -1 end
if ARGV[2] ~= 'core_agent' or ARGV[3] ~= 'oauth2:auth_code:' then return -1 end
if KEYS[1] ~= 'user:auth_code:{' .. ARGV[1] .. '}:{' .. ARGV[2] .. '}' then return -1 end

local current_code = redis.call('GET', KEYS[1])
if not current_code then return 1 end
if #current_code ~= 43 or not string.match(current_code, '^[A-Za-z0-9_-]+$') then return -1 end
redis.call('DEL', ARGV[3] .. '{' .. current_code .. '}')
redis.call('DEL', KEYS[1])
return 1

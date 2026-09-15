if #KEYS ~= 2 or #ARGV ~= 3 then return -1 end
if type(ARGV[1]) ~= 'string' or #ARGV[1] ~= 43 or not string.match(ARGV[1], '^[A-Za-z0-9_-]+$') then return -1 end
if not string.match(ARGV[2], '^[1-9][0-9]*$') then return -1 end
if ARGV[3] ~= 'core_agent' then return -1 end
if KEYS[1] ~= 'oauth2:auth_code:{' .. ARGV[1] .. '}' then return -1 end
if KEYS[2] ~= 'user:auth_code:{' .. ARGV[2] .. '}:{' .. ARGV[3] .. '}' then return -1 end

local current_code = redis.call('GET', KEYS[2])
if not current_code or current_code ~= ARGV[1] then return 0 end
local user_id = redis.call('HGET', KEYS[1], 'user_id')
local client_id = redis.call('HGET', KEYS[1], 'client_id')
if not user_id or user_id ~= ARGV[2] or not client_id or client_id ~= ARGV[3] then return 0 end

redis.call('DEL', KEYS[1])
redis.call('DEL', KEYS[2])
return 1

local fields = {
  'schema_version', 'client_id', 'redirect_uri', 'scopes', 'code_challenge', 'code_challenge_method',
  'original_socket_address', 'oauth_state', 'issued_at_epoch_millis', 'expires_at_epoch_millis',
  'user_id', 'username', 'role_id', 'password_hash', 'email_present', 'email', 'extra_grant_types', 'status'
}

if #KEYS ~= 3 or #ARGV ~= 42 then return -1 end
if not string.match(ARGV[1], '^[1-9][0-9]*$') then return -1 end
if type(ARGV[2]) ~= 'string' or #ARGV[2] ~= 43 or not string.match(ARGV[2], '^[A-Za-z0-9_-]+$') then return -1 end
if type(ARGV[3]) ~= 'string' or #ARGV[3] ~= 43 or not string.match(ARGV[3], '^[A-Za-z0-9_-]+$') then return -1 end
if ARGV[4] ~= 'core_agent' or not string.match(ARGV[5], '^[1-9][0-9]*$') then return -1 end
if type(ARGV[6]) ~= 'string' or #ARGV[6] == 0 then return -1 end
if KEYS[1] ~= 'oauth2:authorize:pending:{' .. ARGV[2] .. '}' then return -1 end
if KEYS[2] ~= 'oauth2:auth_code:{' .. ARGV[3] .. '}' then return -1 end
if KEYS[3] ~= 'user:auth_code:{' .. ARGV[5] .. '}:{' .. ARGV[4] .. '}' then return -1 end
for index, field in ipairs(fields) do
  if ARGV[6 + index * 2 - 1] ~= field then return -1 end
end
if ARGV[8] ~= '1' or ARGV[10] ~= ARGV[4] or ARGV[28] ~= ARGV[5] then return -1 end

if redis.call('HGET', KEYS[1], 'client_id') ~= ARGV[4] then return 0 end
if redis.call('HGET', KEYS[1], 'user_id') ~= ARGV[5] then return 0 end
if redis.call('HGET', KEYS[1], 'session_id') ~= ARGV[6] then return 0 end

redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[2], unpack(ARGV, 7))
redis.call('PEXPIRE', KEYS[2], ARGV[1])
redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[1])
return 1

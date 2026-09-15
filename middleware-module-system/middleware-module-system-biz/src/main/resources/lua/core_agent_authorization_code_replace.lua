local fields = {
  'schema_version', 'client_id', 'redirect_uri', 'scopes', 'code_challenge', 'code_challenge_method',
  'original_socket_address', 'oauth_state', 'issued_at_epoch_millis', 'expires_at_epoch_millis',
  'user_id', 'username', 'role_id', 'password_hash', 'email_present', 'email', 'extra_grant_types', 'status'
}

if #KEYS ~= 2 or #ARGV ~= 40 then return -1 end
if not string.match(ARGV[1], '^[1-9][0-9]*$') then return -1 end
if type(ARGV[2]) ~= 'string' or #ARGV[2] ~= 43 or not string.match(ARGV[2], '^[A-Za-z0-9_-]+$') then return -1 end
if not string.match(ARGV[3], '^[1-9][0-9]*$') or ARGV[4] ~= 'core_agent' then return -1 end
if KEYS[1] ~= 'oauth2:auth_code:{' .. ARGV[2] .. '}' then return -1 end
if KEYS[2] ~= 'user:auth_code:{' .. ARGV[3] .. '}:{' .. ARGV[4] .. '}' then return -1 end
for index, field in ipairs(fields) do
  if ARGV[4 + index * 2 - 1] ~= field then return -1 end
end
if ARGV[6] ~= '1' or ARGV[8] ~= ARGV[4] or ARGV[26] ~= ARGV[3] then return -1 end
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], unpack(ARGV, 5))
redis.call('PEXPIRE', KEYS[1], ARGV[1])
redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[1])
return 1

local fields = {
  'schema_version', 'client_id', 'redirect_uri', 'requested_scopes_present', 'requested_scopes',
  'code_challenge', 'code_challenge_method', 'oauth_state', 'original_socket_address', 'user_id',
  'session_id', 'issued_at_epoch_millis', 'expires_at_epoch_millis'
}

if #KEYS ~= 1 or #ARGV ~= 28 then return -1 end
if not string.match(ARGV[1], '^[1-9][0-9]*$') then return -1 end
if type(ARGV[2]) ~= 'string' or #ARGV[2] ~= 43 or not string.match(ARGV[2], '^[A-Za-z0-9_-]+$') then return -1 end
if KEYS[1] ~= 'oauth2:authorize:pending:{' .. ARGV[2] .. '}' then return -1 end
for index, field in ipairs(fields) do
  if ARGV[2 + index * 2 - 1] ~= field then return -1 end
end
if ARGV[4] ~= '1' or ARGV[6] ~= 'core_agent' then return -1 end

redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], unpack(ARGV, 3))
redis.call('PEXPIRE', KEYS[1], ARGV[1])
return 1

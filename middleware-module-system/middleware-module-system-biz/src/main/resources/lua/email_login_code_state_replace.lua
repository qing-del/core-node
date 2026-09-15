local fields = {
  'schema_version',
  'client_id',
  'user_id',
  'email_fingerprint',
  'verifier_hash',
  'failed_attempts',
  'issued_at_epoch_millis',
  'expires_at_epoch_millis'
}

if #KEYS ~= 1 or #ARGV ~= 17 then return 0 end
if not string.match(ARGV[1], '^[1-9][0-9]*$') then return 0 end
for index, field in ipairs(fields) do
  if ARGV[index * 2] ~= field then return 0 end
end

redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], unpack(ARGV, 2))
redis.call('PEXPIRE', KEYS[1], ARGV[1])
return 1

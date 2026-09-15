-- KEYS[1] is the expected old refresh hash, KEYS[2] is the next refresh hash, KEYS[3] is the client-user session.
-- ARGV carries only the expected fingerprint, fixed OAuth2TokenStateCodec fields, and TTLs.
-- Validate both current pointers before mutating any key so exactly one competing refresh rotation can succeed.
if redis.call('HGET', KEYS[3], 'current_refresh_fingerprint') ~= ARGV[1] then
    return 0
end
if redis.call('HGET', KEYS[1], 'fingerprint') ~= ARGV[1] then
    return 0
end

redis.call('DEL', KEYS[1])
redis.call('DEL', KEYS[2])
redis.call('HSET', KEYS[2], unpack(ARGV, 3, 18))
redis.call('PEXPIRE', KEYS[2], ARGV[2])
redis.call('DEL', KEYS[3])
redis.call('HSET', KEYS[3], unpack(ARGV, 20, 33))
redis.call('PEXPIRE', KEYS[3], ARGV[19])
return 1

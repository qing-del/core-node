-- KEYS[1] is the new refresh fingerprint hash; KEYS[2] is the current client-user session hash.
-- ARGV carries only fixed OAuth2TokenStateCodec fields and TTLs, never bearer credentials.
-- Layout: refresh_ttl, refresh field/value pairs, session_ttl, session field/value pairs.
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], unpack(ARGV, 2, 17))
redis.call('PEXPIRE', KEYS[1], ARGV[1])
redis.call('DEL', KEYS[2])
redis.call('HSET', KEYS[2], unpack(ARGV, 19, 32))
redis.call('PEXPIRE', KEYS[2], ARGV[18])
return 1

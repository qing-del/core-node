-- Atomically append one raw Yjs update and its BindingEnvelope to two streams.
-- ARGV[1] update bytes, ARGV[2] clientUpdateId, ARGV[3] optional operatorId,
-- ARGV[4] operatorType, ARGV[5] createdAt, ARGV[6] BindingEnvelope bytes.
if #KEYS ~= 2 then
    return redis.error_reply('document LINK append requires exactly two keys')
end
if #ARGV ~= 6 then
    return redis.error_reply('document LINK append requires exactly six arguments')
end

local updateRecordId
if ARGV[3] == '' then
    updateRecordId = redis.call('XADD', KEYS[1], '*',
        'update', ARGV[1],
        'clientUpdateId', ARGV[2],
        'operatorType', ARGV[4],
        'createdAt', ARGV[5])
else
    updateRecordId = redis.call('XADD', KEYS[1], '*',
        'update', ARGV[1],
        'clientUpdateId', ARGV[2],
        'operatorId', ARGV[3],
        'operatorType', ARGV[4],
        'createdAt', ARGV[5])
end

local bindingRecordId = redis.call('XADD', KEYS[2], '*',
    'envelope', ARGV[6])

return {updateRecordId, bindingRecordId}

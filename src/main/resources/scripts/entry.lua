-- 응모 한 건을 원자적으로 처리한다.
-- KEYS[1] requestId 키, KEYS[2] Instagram 아이디 간격 제한 키, KEYS[3] 접수 번호 카운터, KEYS[4] 응모 기록 Stream
-- ARGV[1] 간격 제한(ms), ARGV[2] requestId 보관(ms), ARGV[3] Instagram 아이디, ARGV[4] requestId, ARGV[5] 브라우저 고유값, ARGV[6] 접수 시각(ms)
-- 반환: {0, 새 번호} | {1, 처음 발급한 번호(같은 requestId 재요청)} | {2, 0}(간격 제한)

-- 재시도는 간격 제한보다 먼저 확인해야 이미 접수된 요청이 429를 받지 않는다
local issued = redis.call('GET', KEYS[1])
if issued then
  return {1, tonumber(issued)}
end

if not redis.call('SET', KEYS[2], '1', 'PX', ARGV[1], 'NX') then
  return {2, 0}
end

local receipt = redis.call('INCR', KEYS[3])
redis.call('SET', KEYS[1], receipt, 'PX', ARGV[2])
redis.call('XADD', KEYS[4], '*',
  'receiptNumber', receipt,
  'instagramId', ARGV[3],
  'requestId', ARGV[4],
  'clientId', ARGV[5],
  'submittedAt', ARGV[6])
return {0, receipt}

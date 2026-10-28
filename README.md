# desyp-entry

DesyP 메인 이벤트 응모 서비스. 사전등록 백엔드(`desyp`)와 별도로 배포한다. 로그인 없이 Instagram 아이디로 응모를 받고, Redis 한 대에서 접수 번호를 발급한다.

Java 21 · Spring Boot 4.1.1 · Spring Data Redis · Virtual Thread

## 정책

- 응모 자격: 만 14세 이상 누구나. 로그인·사전등록 여부를 묻지 않는다
- 필수 동의 2개: 만 14세 이상, 당첨 연락용 Instagram 아이디 수집·이용
- 1인 무제한 응모. 같은 Instagram 아이디는 1초에 한 번만 접수한다
- 접수 번호 1번은 선착순 당첨. 랜덤 번호는 미리 정해 두고(`EVENT_RANDOM_NUMBER`) 종료 뒤 공개한다. 응모 수가 그보다 적으면 마지막 접수 번호가 당첨
- 이벤트 날짜는 미정(수능 이후). 날짜는 환경 변수로만 받고 기본값이 없다

## API

| 요청 | 응답 |
| --- | --- |
| `GET /api/event/status` | `200 { phase: BEFORE\|OPEN\|CLOSED, startAt, endAt, serverTime, randomNumber }`. `randomNumber`는 종료 뒤에만 값이 있다 |
| `POST /api/event/entries` | 본문 `{ requestId(UUID), instagramId, ageConfirmed: true, agreeInstagramCollection: true }`, 선택 헤더 `X-Client-Id`(UUID v4). 새 응모 `201 { receiptNumber }`, 같은 `requestId` 재요청은 처음 번호로 `200` |

오류는 `{ message }`로 돌려준다. `400` 형식·동의 오류, `410` 응모 시간 아님, `429` 같은 아이디 1초 제한, `503` Redis 장애.

## 처리 방식

`src/main/resources/scripts/entry.lua` 한 번으로 원자 처리한다.

1. 같은 `requestId`로 이미 발급했으면 그 번호를 돌려준다(재시도 멱등, 10분 보관)
2. 같은 Instagram 아이디의 1초 제한(`SET NX PX`)
3. 접수 번호 증가(`INCR entry:seq`)
4. 응모 기록을 Stream `entry:stream`에 추가

Stream을 DB로 옮기는 워커는 이 저장소 범위 밖이다.

## 실행

```bash
docker run -d --name desyp-redis -p 6379:6379 redis:7
EVENT_START_AT=2030-01-05T14:00:00+09:00 \
EVENT_END_AT=2030-01-05T14:10:00+09:00 \
EVENT_RANDOM_NUMBER=7 \
./gradlew bootRun
```

| 환경 변수 | 필수 | 설명 |
| --- | --- | --- |
| `EVENT_START_AT`, `EVENT_END_AT` | O | 응모 시작·종료(ISO 8601, `+09:00`) |
| `EVENT_RANDOM_NUMBER` | O | 미리 정한 랜덤 당첨 번호 |
| `EVENT_ALLOWED_ORIGIN` | X | CORS 허용 주소. 기본 `https://www.desyp.site` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | X | 기본 `localhost:6379` |
| `PORT` | X | 기본 `8081` |

## 테스트

로컬 Redis(`localhost:6379`)가 필요하다. 테스트는 DB 15를 매번 비운다.

```bash
docker run -d --name desyp-redis -p 6379:6379 redis:7   # 이미 떠 있으면 생략
./gradlew test
```

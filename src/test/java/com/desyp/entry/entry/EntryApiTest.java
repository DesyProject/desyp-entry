package com.desyp.entry.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.data.domain.Range;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 실제 Redis(기본 localhost:6379, DB 15)가 필요하다. 테스트마다 DB 15를 비운다.
 * 이벤트 시간은 application-test.yml의 2030-01-05 14:00~14:10 KST다.
 */
@SpringBootTest
@ActiveProfiles("test")
class EntryApiTest {

    private static final Instant BEFORE = Instant.parse("2030-01-05T04:59:00Z");
    private static final Instant OPEN = Instant.parse("2030-01-05T05:01:00Z");
    private static final Instant CLOSED = Instant.parse("2030-01-05T05:11:00Z");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private EntryStore entryStore;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private Clock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        at(OPEN);
    }

    @Test
    void newEntriesReceiveIncreasingRanks() throws Exception {
        String firstRequestId = uuid();
        submit(firstRequestId, "first.user")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(firstRequestId))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.receiptNumber").value(1))
                .andExpect(jsonPath("$.created").value(true));
        submit(uuid(), "second.user")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank").value(2));
    }

    @Test
    void sameRequestIdReturnsFirstRankWithoutRateLimit() throws Exception {
        String requestId = uuid();
        submit(requestId, "retry.user")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.created").value(true));
        submit(requestId, "retry.user")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.receiptNumber").value(1))
                .andExpect(jsonPath("$.created").value(false));
        assertThat(redis.opsForStream().size(entryStore.streamKey())).isEqualTo(1);
    }

    @Test
    void sameInstagramIdWithinOneSecondIsRateLimited() throws Exception {
        submit(uuid(), "fast.user").andExpect(status().isCreated());
        submit(uuid(), "fast.user").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").exists());
        submit(uuid(), "other.user").andExpect(status().isCreated()).andExpect(jsonPath("$.rank").value(2));
    }

    @Test
    void instagramIdIsNormalizedBeforeRateLimit() throws Exception {
        submit(uuid(), "@Test.User").andExpect(status().isCreated());
        submit(uuid(), "test.user").andExpect(status().isTooManyRequests());
    }

    @Test
    void sameInstagramIdCanEnterAgainAfterOneSecond() throws Exception {
        submit(uuid(), "patient.user").andExpect(status().isCreated());
        Thread.sleep(1100);
        submit(uuid(), "patient.user").andExpect(status().isCreated()).andExpect(jsonPath("$.rank").value(2));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        submit(uuid(), "bad id!").andExpect(status().isBadRequest());
        submit("not-a-uuid", "good.id").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/event/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uuid(), "good.id", true, false)))
                .andExpect(status().isBadRequest());
        assertThat(redis.opsForValue().get(entryStore.counterKey())).isNull();
    }

    @Test
    void entryOutsideEventTimeIsGone() throws Exception {
        at(BEFORE);
        submit(uuid(), "early.user").andExpect(status().isGone());
        at(CLOSED);
        submit(uuid(), "late.user").andExpect(status().isGone());
    }

    @Test
    void entryIsRecordedWithRelayWriterContract() throws Exception {
        String clientId = uuid();
        String requestId = uuid();
        mockMvc.perform(post("/api/event/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Client-Id", clientId)
                        .content(body(requestId, "stream.user", true, true)))
                .andExpect(status().isCreated());
        var records = redis.opsForStream().range(entryStore.streamKey(), Range.unbounded());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getValue())
                .containsEntry("requestId", requestId)
                .containsEntry("instagramId", "stream.user")
                .containsEntry("eventId", "event-001")
                .containsEntry("rank", "1")
                .containsEntry("acceptedAt", OPEN.toString());
    }

    @Test
    void statusRevealsWinningNumbersOnlyAfterClose() throws Exception {
        at(BEFORE);
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.phase").value("BEFORE"))
                .andExpect(jsonPath("$.winningNumbers").doesNotExist());

        at(OPEN);
        for (int i = 0; i < 3; i++) {
            submit(uuid(), "user" + i).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.phase").value("OPEN"))
                .andExpect(jsonPath("$.winningNumbers").doesNotExist());

        at(CLOSED);
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.phase").value("CLOSED"))
                .andExpect(jsonPath("$.winningNumbers.length()").value(3))
                .andExpect(jsonPath("$.winningNumbers[0]").value(1))
                .andExpect(jsonPath("$.winningNumbers[2]").value(3));

        at(OPEN);
        for (int i = 3; i < 20; i++) {
            submit(uuid(), "user" + i).andExpect(status().isCreated());
        }
        at(CLOSED);
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.winningNumbers.length()").value(18))
                .andExpect(jsonPath("$.winningNumbers[0]").value(1))
                .andExpect(jsonPath("$.winningNumbers[17]").value(18));
    }

    @Test
    void closedEventWithoutEntriesHasNoWinningNumbers() throws Exception {
        at(CLOSED);
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.phase").value("CLOSED"))
                .andExpect(jsonPath("$.winningNumbers").doesNotExist());
    }

    @Test
    void counterDoesNotExpire() throws Exception {
        submit(uuid(), "counter.user").andExpect(status().isCreated());

        assertThat(redis.getExpire(entryStore.counterKey())).isEqualTo(-1L);
        assertThat(redis.getExpire(entryStore.requestsKey())).isPositive();
    }

    @Test
    void concurrentEntriesReceiveUniqueSequentialNumbers() throws Exception {
        int count = 300;
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String instagramId = "load.user" + i;
            tasks.add(() -> {
                MvcResult result = submit(uuid(), instagramId).andExpect(status().isCreated()).andReturn();
                String json = result.getResponse().getContentAsString();
                return objectMapper.readTree(json).get("rank").longValue();
            });
        }
        Set<Long> numbers = new HashSet<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Long> future : pool.invokeAll(tasks)) {
                numbers.add(future.get());
            }
        }
        assertThat(numbers).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, count).boxed().toList());
        assertThat(redis.opsForStream().size(entryStore.streamKey())).isEqualTo(count);
    }

    @Test
    void allRedisKeysUseTheSameClusterHashTag() {
        assertThat(entryStore.counterKey()).contains("{event-001}");
        assertThat(entryStore.requestsKey()).contains("{event-001}");
        assertThat(entryStore.streamKey()).contains("{event-001}");
        assertThat(entryStore.rateKey("test.user")).contains("{event-001}");
    }

    @Test
    void corsAllowsConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/api/event/entries")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:4173"));
    }

    private void at(Instant instant) {
        given(clock.instant()).willReturn(instant);
        given(clock.millis()).willReturn(instant.toEpochMilli());
    }

    private ResultActions submit(String requestId, String instagramId)
            throws Exception {
        return mockMvc.perform(post("/api/event/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(requestId, instagramId, true, true)));
    }

    private static String body(String requestId, String instagramId, boolean age, boolean consent) {
        return """
                {"requestId":"%s","instagramId":"%s","ageConfirmed":%s,"agreeInstagramCollection":%s}
                """.formatted(requestId, instagramId, age, consent);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}

package com.desyp.entry.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void newEntriesReceiveIncreasingReceiptNumbers() throws Exception {
        submit(uuid(), "first.user").andExpect(status().isCreated()).andExpect(jsonPath("$.receiptNumber").value(1));
        submit(uuid(), "second.user")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").value(2));
    }

    @Test
    void sameRequestIdReturnsFirstReceiptNumberWithoutRateLimit() throws Exception {
        String requestId = uuid();
        submit(requestId, "retry.user").andExpect(status().isCreated()).andExpect(jsonPath("$.receiptNumber").value(1));
        submit(requestId, "retry.user").andExpect(status().isOk()).andExpect(jsonPath("$.receiptNumber").value(1));
        assertThat(redis.opsForStream().size(EntryStore.STREAM_KEY)).isEqualTo(1);
    }

    @Test
    void sameInstagramIdWithinOneSecondIsRateLimited() throws Exception {
        submit(uuid(), "fast.user").andExpect(status().isCreated());
        submit(uuid(), "fast.user").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").exists());
        submit(uuid(), "other.user").andExpect(status().isCreated()).andExpect(jsonPath("$.receiptNumber").value(2));
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
        submit(uuid(), "patient.user").andExpect(status().isCreated()).andExpect(jsonPath("$.receiptNumber").value(2));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        submit(uuid(), "bad id!").andExpect(status().isBadRequest());
        submit("not-a-uuid", "good.id").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/event/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uuid(), "good.id", true, false)))
                .andExpect(status().isBadRequest());
        assertThat(redis.opsForValue().get(EntryStore.SEQUENCE_KEY)).isNull();
    }

    @Test
    void entryOutsideEventTimeIsGone() throws Exception {
        at(BEFORE);
        submit(uuid(), "early.user").andExpect(status().isGone());
        at(CLOSED);
        submit(uuid(), "late.user").andExpect(status().isGone());
    }

    @Test
    void entryIsRecordedInStreamWithClientId() throws Exception {
        String clientId = uuid();
        mockMvc.perform(post("/api/event/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Client-Id", clientId)
                        .content(body(uuid(), "stream.user", true, true)))
                .andExpect(status().isCreated());
        var records = redis.opsForStream().range(EntryStore.STREAM_KEY, Range.unbounded());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getValue())
                .containsEntry("receiptNumber", "1")
                .containsEntry("instagramId", "stream.user")
                .containsEntry("clientId", clientId);
    }

    @Test
    void statusRevealsRandomNumberOnlyAfterClose() throws Exception {
        at(BEFORE);
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.phase").value("BEFORE"))
                .andExpect(jsonPath("$.randomNumber").doesNotExist());

        at(OPEN);
        for (int i = 0; i < 3; i++) {
            submit(uuid(), "user" + i).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.phase").value("OPEN"))
                .andExpect(jsonPath("$.randomNumber").doesNotExist());

        // 응모 3건 < 정해 둔 번호 7 → 마지막 번호 3이 당첨
        at(CLOSED);
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.phase").value("CLOSED"))
                .andExpect(jsonPath("$.randomNumber").value(3));

        at(OPEN);
        for (int i = 3; i < 10; i++) {
            submit(uuid(), "user" + i).andExpect(status().isCreated());
        }
        at(CLOSED);
        mockMvc.perform(get("/api/event/status")).andExpect(jsonPath("$.randomNumber").value(7));
    }

    @Test
    void closedEventWithoutEntriesHasNoRandomNumber() throws Exception {
        at(CLOSED);
        mockMvc.perform(get("/api/event/status"))
                .andExpect(jsonPath("$.phase").value("CLOSED"))
                .andExpect(jsonPath("$.randomNumber").doesNotExist());
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
                return Long.parseLong(json.replaceAll("\\D", ""));
            });
        }
        Set<Long> numbers = new HashSet<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Long> future : pool.invokeAll(tasks)) {
                numbers.add(future.get());
            }
        }
        assertThat(numbers).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, count).boxed().toList());
        assertThat(redis.opsForStream().size(EntryStore.STREAM_KEY)).isEqualTo(count);
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
